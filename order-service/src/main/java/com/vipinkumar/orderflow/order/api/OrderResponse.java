package com.vipinkumar.orderflow.order.api;

import com.vipinkumar.orderflow.order.domain.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        String status,
        String statusReason,
        String paymentMethod,
        BigDecimal totalAmount,
        String currency,
        UUID paymentId,
        List<Line> lines,
        Instant createdAt,
        Instant updatedAt) {

    public record Line(String sku, int quantity, BigDecimal unitPrice) {}

    public static OrderResponse from(Order o) {
        return new OrderResponse(o.getId(), o.getCustomerId(), o.getStatus().name(), o.getStatusReason(),
                o.getPaymentMethod(), o.getTotalAmount(), o.getCurrency(), o.getPaymentId(),
                o.getLines().stream().map(l -> new Line(l.getSku(), l.getQuantity(), l.getUnitPrice())).toList(),
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
