package com.vipinkumar.orderflow.order.saga;

import com.vipinkumar.orderflow.order.app.OrderEvents;
import com.vipinkumar.orderflow.order.domain.IdempotencyRepository;
import com.vipinkumar.orderflow.order.domain.Order;
import com.vipinkumar.orderflow.order.domain.OrderRepository;
import com.vipinkumar.orderflow.order.domain.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/**
 * Sagas need timeouts: if a participant is down, an order must not sit in PENDING forever.
 * Stuck orders are compensated (inventory release is idempotent and safe even if nothing was held).
 */
@Component
public class SagaWatchdog {

    private static final Logger log = LoggerFactory.getLogger(SagaWatchdog.class);

    private final OrderRepository orders;
    private final IdempotencyRepository idempotency;
    private final OrderEvents events;
    private final Clock clock;
    private final Duration stepTimeout;
    private final Duration keyRetention;

    public SagaWatchdog(OrderRepository orders, IdempotencyRepository idempotency, OrderEvents events, Clock clock,
                        @Value("${orderflow.saga.step-timeout:PT2M}") Duration stepTimeout,
                        @Value("${orderflow.idempotency.retention:PT24H}") Duration keyRetention) {
        this.orders = orders;
        this.idempotency = idempotency;
        this.events = events;
        this.clock = clock;
        this.stepTimeout = stepTimeout;
        this.keyRetention = keyRetention;
    }

    @Scheduled(fixedDelayString = "${orderflow.saga.watchdog-interval-ms:15000}")
    @Transactional
    public void cancelStuckOrders() {
        Instant cutoff = clock.instant().minus(stepTimeout);
        List<Order> stuck = orders.findTop100ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                EnumSet.of(OrderStatus.PENDING, OrderStatus.RESERVED), cutoff);
        for (Order order : stuck) {
            String reason = "Timed out waiting in " + order.getStatus();
            OrderStatus target = order.getStatus() == OrderStatus.PENDING ? OrderStatus.REJECTED : OrderStatus.CANCELLING;
            OrderStatus from = order.transitionTo(target, reason, clock.instant());
            events.statusChanged(order, from);
            events.releaseInventory(order, reason);
            log.warn("Order {} {}: {}", order.getId(), target, reason);
        }
    }

    @Scheduled(cron = "${orderflow.idempotency.cleanup-cron:0 15 * * * *}")
    @Transactional
    public void purgeOldIdempotencyKeys() {
        idempotency.deleteOlderThan(clock.instant().minus(keyRetention));
    }
}
