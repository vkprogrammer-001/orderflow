package com.vipinkumar.orderflow.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Payload records carried inside {@link EventEnvelope#payload()}. */
public final class Payloads {
    private Payloads() {}

    public record Line(String sku, int quantity) {}

    public record ReserveInventory(UUID orderId, List<Line> lines) {}

    public record ReleaseInventory(UUID orderId, String reason) {}

    /** Unit prices come from the inventory catalogue, never from the client, so the order total can't be tampered with. */
    public record PricedLine(String sku, int quantity, BigDecimal unitPrice) {}

    public record InventoryReserved(UUID orderId, List<PricedLine> lines, String currency) {}

    public record InventoryRejected(UUID orderId, String sku, String reason) {}

    public record InventoryReleased(UUID orderId) {}

    public record AuthorizePayment(UUID orderId, String customerId, BigDecimal amount,
                                   String currency, String method) {}

    public record PaymentAuthorized(UUID orderId, UUID paymentId, String providerReference) {}

    public record PaymentDeclined(UUID orderId, String reason) {}

    public record OrderStatusChanged(UUID orderId, String from, String to, String reason) {}
}
