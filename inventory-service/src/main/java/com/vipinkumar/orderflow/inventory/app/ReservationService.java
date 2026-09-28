package com.vipinkumar.orderflow.inventory.app;

import com.vipinkumar.orderflow.events.EventCodec;
import com.vipinkumar.orderflow.events.EventEnvelope;
import com.vipinkumar.orderflow.events.EventTypes;
import com.vipinkumar.orderflow.events.Payloads;
import com.vipinkumar.orderflow.inventory.domain.Product;
import com.vipinkumar.orderflow.inventory.domain.ProductRepository;
import com.vipinkumar.orderflow.inventory.domain.Reservation;
import com.vipinkumar.orderflow.inventory.domain.ReservationRepository;
import com.vipinkumar.orderflow.inventory.domain.ReservationStatus;
import com.vipinkumar.orderflow.outbox.InboxGuard;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Saga participant: holds and releases stock in response to order commands. */
@Service
public class ReservationService {

    static final String CONSUMER = "inventory";
    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ProductRepository products;
    private final ReservationRepository reservations;
    private final InventoryEvents events;
    private final InboxGuard inbox;
    private final EventCodec codec;
    private final TransactionTemplate tx;
    private final CacheManager caches;
    private final Clock clock;
    private final MeterRegistry meters;

    public ReservationService(ProductRepository products, ReservationRepository reservations, InventoryEvents events,
                              InboxGuard inbox, EventCodec codec, TransactionTemplate tx, CacheManager caches,
                              Clock clock, MeterRegistry meters) {
        this.products = products;
        this.reservations = reservations;
        this.events = events;
        this.inbox = inbox;
        this.codec = codec;
        this.tx = tx;
        this.caches = caches;
        this.clock = clock;
        this.meters = meters;
    }

    public void handle(EventEnvelope command) {
        switch (command.type()) {
            case EventTypes.RESERVE_INVENTORY -> reserve(command);
            case EventTypes.RELEASE_INVENTORY -> release(command);
            default -> log.debug("Inventory ignores {}", command.type());
        }
    }

    void reserve(EventEnvelope command) {
        var cmd = codec.payload(command, Payloads.ReserveInventory.class);
        try {
            tx.executeWithoutResult(status -> {
                if (inbox.firstDelivery(CONSUMER, command.eventId())) {
                    reserveInTx(cmd, command.correlationId());
                }
            });
        } catch (ReservationRefusedException refused) {
            // The first transaction rolled back every partial decrement. Record the refusal in a fresh one.
            tx.executeWithoutResult(status -> {
                if (inbox.firstDelivery(CONSUMER, command.eventId())) {
                    reservations.save(Reservation.rejected(cmd.orderId(), refused.getMessage(), clock.instant()));
                    events.rejected(cmd.orderId(), refused.sku(), refused.getMessage(), command.correlationId());
                    meters.counter("inventory.reservations", "result", "rejected").increment();
                }
            });
        }
    }

    private void reserveInTx(Payloads.ReserveInventory cmd, String correlationId) {
        var existing = reservations.findById(cmd.orderId());
        if (existing.isPresent()) {
            Reservation r = existing.get();
            if (r.getStatus() == ReservationStatus.HELD) {
                events.reserved(r, currencyOf(r), correlationId); // re-send the answer, hold nothing extra
            } else {
                events.rejected(cmd.orderId(), null, "Order was already cancelled", correlationId);
            }
            return;
        }

        // Sorted by SKU: every transaction locks product rows in the same order, so two
        // multi-line orders can't deadlock each other.
        Map<String, Integer> wanted = new TreeMap<>();
        cmd.lines().forEach(l -> wanted.merge(l.sku(), l.quantity(), Integer::sum));

        Map<String, Product> catalogue = products.findAllById(wanted.keySet()).stream()
                .collect(Collectors.toMap(Product::getSku, Function.identity()));

        String currency = null;
        List<Reservation.HeldLine> held = new ArrayList<>();
        for (var entry : wanted.entrySet()) {
            String sku = entry.getKey();
            int qty = entry.getValue();
            Product product = catalogue.get(sku);
            if (product == null) {
                throw new ReservationRefusedException(sku, "Unknown SKU " + sku);
            }
            if (currency == null) {
                currency = product.getCurrency();
            } else if (!currency.equals(product.getCurrency())) {
                throw new ReservationRefusedException(sku, "Order mixes currencies");
            }
            if (products.tryReserve(sku, qty) == 0) {
                throw new ReservationRefusedException(sku, "Not enough stock for " + sku);
            }
            held.add(new Reservation.HeldLine(sku, qty, product.getUnitPrice()));
        }

        Reservation reservation = reservations.save(Reservation.held(cmd.orderId(), held, clock.instant()));
        events.reserved(reservation, currency, correlationId);
        evict(wanted.keySet());
        meters.counter("inventory.reservations", "result", "held").increment();
    }

    void release(EventEnvelope command) {
        var cmd = codec.payload(command, Payloads.ReleaseInventory.class);
        tx.executeWithoutResult(status -> {
            if (!inbox.firstDelivery(CONSUMER, command.eventId())) {
                return;
            }
            var existing = reservations.findById(cmd.orderId());
            if (existing.isEmpty()) {
                // Release overtook reserve (e.g. watchdog timeout). Leave a tombstone so a late reserve is refused.
                reservations.save(Reservation.releasedTombstone(cmd.orderId(), cmd.reason(), clock.instant()));
            } else if (existing.get().getStatus() == ReservationStatus.HELD) {
                Reservation r = existing.get();
                for (var line : r.getLines()) {
                    if (products.release(line.getSku(), line.getQuantity()) == 0) {
                        log.error("Reserved count for {} lower than held quantity on order {}", line.getSku(), r.getOrderId());
                    }
                }
                r.markReleased(cmd.reason(), clock.instant());
                reservations.save(r);
                evict(r.getLines().stream().map(Reservation.HeldLine::getSku).toList());
                meters.counter("inventory.reservations", "result", "released").increment();
            }
            // Always answer, so the saga can finish even if this is a repeat.
            events.released(cmd.orderId(), command.correlationId());
        });
    }

    private String currencyOf(Reservation r) {
        return r.getLines().isEmpty() ? null
                : products.findById(r.getLines().get(0).getSku()).map(Product::getCurrency).orElse(null);
    }

    /** The cache manager is transaction-aware, so evictions only happen if this transaction commits. */
    private void evict(Iterable<String> skus) {
        Cache cache = caches.getCache(ProductCatalog.CACHE);
        if (cache != null) {
            skus.forEach(cache::evict);
        }
    }
}
