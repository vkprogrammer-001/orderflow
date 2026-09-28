package com.vipinkumar.orderflow.order.saga;

import com.vipinkumar.orderflow.events.EventCodec;
import com.vipinkumar.orderflow.events.EventEnvelope;
import com.vipinkumar.orderflow.events.EventTypes;
import com.vipinkumar.orderflow.events.Payloads;
import com.vipinkumar.orderflow.events.Topics;
import com.vipinkumar.orderflow.order.app.OrderEvents;
import com.vipinkumar.orderflow.order.domain.Order;
import com.vipinkumar.orderflow.order.domain.OrderRepository;
import com.vipinkumar.orderflow.order.domain.OrderStatus;
import com.vipinkumar.orderflow.outbox.InboxGuard;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrated saga for "place order". Reacts to participant replies and issues the next command
 * or the compensating one:
 *
 * <pre>
 *  ReserveInventory ─▶ InventoryReserved ─▶ AuthorizePayment ─▶ PaymentAuthorized ─▶ CONFIRMED
 *                   └▶ InventoryRejected ─▶ REJECTED        └▶ PaymentDeclined ─▶ ReleaseInventory ─▶ InventoryReleased ─▶ CANCELLED
 * </pre>
 *
 * Each reply is handled in one DB transaction that (1) records the event id in the inbox,
 * (2) updates the order and (3) writes the next command to the outbox. A crash anywhere replays
 * cleanly: either all three happened or none did.
 */
@Component
public class OrderSagaOrchestrator {

    static final String CONSUMER = "order-saga";
    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final OrderRepository orders;
    private final OrderEvents events;
    private final InboxGuard inbox;
    private final EventCodec codec;
    private final Clock clock;
    private final MeterRegistry meters;

    public OrderSagaOrchestrator(OrderRepository orders, OrderEvents events, InboxGuard inbox,
                                 EventCodec codec, Clock clock, MeterRegistry meters) {
        this.orders = orders;
        this.events = events;
        this.inbox = inbox;
        this.codec = codec;
        this.clock = clock;
        this.meters = meters;
    }

    /** Transaction boundary is here (the proxied entry point), not on {@link #handle}, to avoid self-invocation. */
    @KafkaListener(topics = {Topics.INVENTORY_EVENTS, Topics.PAYMENT_EVENTS}, groupId = CONSUMER)
    @Transactional
    public void onMessage(String message) {
        handle(codec.fromJson(message));
    }

    public void handle(EventEnvelope event) {
        MDC.put("orderId", event.aggregateId());
        try {
            if (!inbox.firstDelivery(CONSUMER, event.eventId())) {
                log.debug("Duplicate {} {} ignored", event.type(), event.eventId());
                meters.counter("saga.duplicates").increment();
                return;
            }
            switch (event.type()) {
                case EventTypes.INVENTORY_RESERVED -> onReserved(codec.payload(event, Payloads.InventoryReserved.class));
                case EventTypes.INVENTORY_REJECTED -> onRejected(codec.payload(event, Payloads.InventoryRejected.class));
                case EventTypes.PAYMENT_AUTHORIZED -> onPaid(codec.payload(event, Payloads.PaymentAuthorized.class));
                case EventTypes.PAYMENT_DECLINED -> onDeclined(codec.payload(event, Payloads.PaymentDeclined.class));
                case EventTypes.INVENTORY_RELEASED -> onReleased(codec.payload(event, Payloads.InventoryReleased.class));
                default -> log.debug("Saga ignores {}", event.type());
            }
        } finally {
            MDC.remove("orderId");
        }
    }

    private void onReserved(Payloads.InventoryReserved reply) {
        withOrder(reply.orderId(), OrderStatus.RESERVED, order -> {
            Map<String, BigDecimal> prices = reply.lines().stream()
                    .collect(Collectors.toMap(Payloads.PricedLine::sku, Payloads.PricedLine::unitPrice));
            order.applyPrices(prices, reply.currency());
            OrderStatus from = order.transitionTo(OrderStatus.RESERVED, "Stock held", clock.instant());
            events.statusChanged(order, from);
            events.authorizePayment(order);
        });
    }

    private void onRejected(Payloads.InventoryRejected reply) {
        withOrder(reply.orderId(), OrderStatus.REJECTED, order -> {
            OrderStatus from = order.transitionTo(OrderStatus.REJECTED, reply.reason(), clock.instant());
            events.statusChanged(order, from);
        });
    }

    private void onPaid(Payloads.PaymentAuthorized reply) {
        withOrder(reply.orderId(), OrderStatus.CONFIRMED, order -> {
            order.recordPayment(reply.paymentId());
            OrderStatus from = order.transitionTo(OrderStatus.CONFIRMED, "Payment authorised", clock.instant());
            events.statusChanged(order, from);
        });
    }

    private void onDeclined(Payloads.PaymentDeclined reply) {
        withOrder(reply.orderId(), OrderStatus.CANCELLING, order -> {
            OrderStatus from = order.transitionTo(OrderStatus.CANCELLING, reply.reason(), clock.instant());
            events.statusChanged(order, from);
            events.releaseInventory(order, reply.reason()); // compensation
        });
    }

    private void onReleased(Payloads.InventoryReleased reply) {
        withOrder(reply.orderId(), OrderStatus.CANCELLED, order -> {
            OrderStatus from = order.transitionTo(OrderStatus.CANCELLED, order.getStatusReason(), clock.instant());
            events.statusChanged(order, from);
        });
    }

    /**
     * Applies a step only if the state machine allows it. Late or out-of-order replies (for example a
     * payment that lands after the watchdog already cancelled the order) are logged and counted rather
     * than thrown, because retrying them would never succeed and would block the partition.
     */
    private void withOrder(UUID orderId, OrderStatus target, java.util.function.Consumer<Order> step) {
        Order order = orders.findWithLinesById(orderId).orElse(null);
        if (order == null) {
            log.warn("Saga reply for unknown order {}", orderId);
            meters.counter("saga.unknown_order").increment();
            return;
        }
        if (!order.getStatus().canTransitionTo(target)) {
            log.warn("Order {} is {}; ignoring move to {}", orderId, order.getStatus(), target);
            meters.counter("saga.stale_reply", "target", target.name()).increment();
            return;
        }
        step.accept(order);
        meters.counter("saga.transitions", "to", target.name()).increment();
    }
}
