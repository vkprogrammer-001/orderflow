package com.vipinkumar.orderflow.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Aggregate root. All state changes go through methods here so the invariants live in one class. */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "payment_method", nullable = false, length = 16)
    private String paymentMethod;

    @Column(name = "total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(length = 3)
    private String currency;

    @Column(name = "payment_id")
    private UUID paymentId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic lock: two saga replies racing on the same order can't silently overwrite each other. */
    @Version
    private Long version;

    protected Order() {}

    public static Order place(String customerId, String paymentMethod, Map<String, Integer> quantitiesBySku, Instant now) {
        if (quantitiesBySku.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one line");
        }
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.paymentMethod = paymentMethod;
        order.status = OrderStatus.PENDING;
        order.createdAt = now;
        order.updatedAt = now;
        quantitiesBySku.forEach((sku, qty) -> order.lines.add(new OrderLine(order, sku, qty)));
        return order;
    }

    /** Moves to {@code next} or throws; returns the previous status for event publishing. */
    public OrderStatus transitionTo(OrderStatus next, String reason, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalTransitionException(status, next);
        }
        OrderStatus previous = status;
        status = next;
        statusReason = reason;
        updatedAt = now;
        return previous;
    }

    /** Applies catalogue prices from inventory and computes the authoritative total. */
    public void applyPrices(Map<String, BigDecimal> unitPriceBySku, String currency) {
        for (OrderLine line : lines) {
            BigDecimal price = unitPriceBySku.get(line.getSku());
            if (price == null) {
                throw new IllegalArgumentException("No price returned for " + line.getSku());
            }
            line.price(price);
        }
        this.currency = currency;
        this.totalAmount = lines.stream().map(OrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);
    }

    public void recordPayment(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public String getStatusReason() { return statusReason; }
    public String getPaymentMethod() { return paymentMethod; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public UUID getPaymentId() { return paymentId; }
    public List<OrderLine> getLines() { return Collections.unmodifiableList(lines); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
