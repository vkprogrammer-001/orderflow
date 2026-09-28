package com.vipinkumar.orderflow.order.app;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID id) {
        super("Order " + id + " was not found");
    }
}
