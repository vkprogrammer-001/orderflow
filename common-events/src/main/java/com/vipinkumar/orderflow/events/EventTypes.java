package com.vipinkumar.orderflow.events;

/** String constants for {@link EventEnvelope#type()}. Commands are imperative, events are past tense. */
public final class EventTypes {
    private EventTypes() {}

    // commands issued by the order saga
    public static final String RESERVE_INVENTORY = "ReserveInventory";
    public static final String RELEASE_INVENTORY = "ReleaseInventory";
    public static final String AUTHORIZE_PAYMENT = "AuthorizePayment";

    // replies from participants
    public static final String INVENTORY_RESERVED = "InventoryReserved";
    public static final String INVENTORY_REJECTED = "InventoryRejected";
    public static final String INVENTORY_RELEASED = "InventoryReleased";
    public static final String PAYMENT_AUTHORIZED = "PaymentAuthorized";
    public static final String PAYMENT_DECLINED = "PaymentDeclined";

    // order lifecycle (consumed by audit / downstream systems)
    public static final String ORDER_STATUS_CHANGED = "OrderStatusChanged";
}
