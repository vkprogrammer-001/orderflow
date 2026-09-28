package com.vipinkumar.orderflow.events;

/** Kafka topic names. One topic per producer/intent keeps ACLs and retention policies simple. */
public final class Topics {
    private Topics() {}

    public static final String INVENTORY_COMMANDS = "inventory.commands";
    public static final String PAYMENT_COMMANDS = "payment.commands";
    public static final String INVENTORY_EVENTS = "inventory.events";
    public static final String PAYMENT_EVENTS = "payment.events";
    public static final String ORDER_EVENTS = "order.events";
}
