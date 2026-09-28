package com.vipinkumar.orderflow.order.domain;

public class IllegalTransitionException extends RuntimeException {
    public IllegalTransitionException(OrderStatus from, OrderStatus to) {
        super("Order cannot move from " + from + " to " + to);
    }
}
