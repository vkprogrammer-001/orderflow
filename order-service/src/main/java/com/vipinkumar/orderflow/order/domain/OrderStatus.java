package com.vipinkumar.orderflow.order.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order lifecycle as an explicit state machine (State pattern, enum flavour).
 *
 * <pre>
 *  PENDING ──reserved──▶ RESERVED ──paid──▶ CONFIRMED
 *     │                     │
 *     └─out of stock─▶ REJECTED     └─declined/timeout─▶ CANCELLING ──released──▶ CANCELLED
 * </pre>
 *
 * Keeping the allowed transitions in one place means a duplicated or out-of-order event can never
 * move an order backwards (e.g. a late "InventoryReserved" can't resurrect a REJECTED order).
 */
public enum OrderStatus {
    PENDING,
    RESERVED,
    CONFIRMED,
    REJECTED,
    CANCELLING,
    CANCELLED;

    private Set<OrderStatus> next;

    static {
        PENDING.next = EnumSet.of(RESERVED, REJECTED);
        RESERVED.next = EnumSet.of(CONFIRMED, CANCELLING);
        CANCELLING.next = EnumSet.of(CANCELLED);
        CONFIRMED.next = EnumSet.noneOf(OrderStatus.class);
        REJECTED.next = EnumSet.noneOf(OrderStatus.class);
        CANCELLED.next = EnumSet.noneOf(OrderStatus.class);
    }

    public boolean canTransitionTo(OrderStatus target) {
        return next.contains(target);
    }

    public boolean isTerminal() {
        return next.isEmpty();
    }
}
