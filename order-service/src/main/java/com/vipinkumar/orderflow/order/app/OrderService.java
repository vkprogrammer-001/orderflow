package com.vipinkumar.orderflow.order.app;

import com.vipinkumar.orderflow.order.api.CreateOrderRequest;
import com.vipinkumar.orderflow.order.domain.IdempotencyRecord;
import com.vipinkumar.orderflow.order.domain.IdempotencyRepository;
import com.vipinkumar.orderflow.order.domain.Order;
import com.vipinkumar.orderflow.order.domain.OrderRepository;
import com.vipinkumar.orderflow.order.domain.OrderStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final IdempotencyRepository idempotency;
    private final OrderEvents events;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;
    private final Clock clock;
    private final MeterRegistry meters;

    public OrderService(OrderRepository orders, IdempotencyRepository idempotency, OrderEvents events,
                        TransactionTemplate tx, Clock clock, MeterRegistry meters) {
        this.orders = orders;
        this.idempotency = idempotency;
        this.events = events;
        this.tx = tx;
        this.readTx = new TransactionTemplate(tx.getTransactionManager());
        this.readTx.setReadOnly(true);
        this.clock = clock;
        this.meters = meters;
    }

    /**
     * Places an order exactly once per {@code Idempotency-Key}.
     *
     * <p>The order row, the key and the first saga command commit in one transaction. If two requests
     * with the same key race, the primary key on {@code idempotency_keys} lets exactly one commit; the
     * loser catches the violation and returns the winner's order.
     */
    public PlacedOrder place(CreateOrderRequest request, String idempotencyKey) {
        String fingerprint = RequestFingerprint.of(request);
        try {
            PlacedOrder placed = tx.execute(status -> placeInTx(request, idempotencyKey, fingerprint));
            meters.counter("orders.placed", "replayed", String.valueOf(placed.replayed())).increment();
            return placed;
        } catch (DataIntegrityViolationException raceLost) {
            meters.counter("orders.placed", "replayed", "true").increment();
            return tx.execute(status -> replay(idempotencyKey, fingerprint));
        }
    }

    private PlacedOrder placeInTx(CreateOrderRequest request, String key, String fingerprint) {
        var existing = idempotency.findById(key);
        if (existing.isPresent()) {
            return replay(existing.get(), key, fingerprint);
        }

        Map<String, Integer> quantities = new LinkedHashMap<>();
        request.lines().forEach(l -> quantities.merge(l.sku(), l.quantity(), Integer::sum));

        Order order = Order.place(request.customerId(), request.paymentMethod(), quantities, clock.instant());
        orders.save(order);
        idempotency.saveAndFlush(new IdempotencyRecord(key, fingerprint, order.getId(), clock.instant()));

        events.statusChanged(order, null);
        events.reserveInventory(order);
        return new PlacedOrder(order, false);
    }

    private PlacedOrder replay(String key, String fingerprint) {
        IdempotencyRecord record = idempotency.findById(key)
                .orElseThrow(() -> new IllegalStateException("Idempotency record vanished for " + key));
        return replay(record, key, fingerprint);
    }

    private PlacedOrder replay(IdempotencyRecord record, String key, String fingerprint) {
        if (!record.getRequestHash().equals(fingerprint)) {
            throw new IdempotencyKeyReusedException(key);
        }
        Order order = orders.findWithLinesById(record.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(record.getOrderId()));
        return new PlacedOrder(order, true);
    }

    public Order get(UUID id) {
        return readTx.execute(status -> orders.findWithLinesById(id)
                .orElseThrow(() -> new OrderNotFoundException(id)));
    }

    public Page<Order> list(OrderStatus status, Pageable pageable) {
        return readTx.execute(s -> {
            Page<Order> page = status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable);
            page.forEach(o -> o.getLines().size()); // initialise lines inside the tx (open-in-view is off)
            return page;
        });
    }
}
