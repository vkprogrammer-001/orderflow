package com.vipinkumar.orderflow.payment.domain;

import com.vipinkumar.orderflow.payment.provider.AuthorizationResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    /** Unique: the database, not application code, guarantees one payment per order. */
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 16)
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "provider_reference", length = 64)
    private String providerReference;

    @Column(name = "decline_reason", length = 255)
    private String declineReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected Payment() {}

    public static Payment record(UUID orderId, String customerId, BigDecimal amount, String currency, String method,
                                 AuthorizationResult result, Instant now) {
        Payment p = new Payment();
        p.id = UUID.randomUUID();
        p.orderId = orderId;
        p.customerId = customerId;
        p.amount = amount;
        p.currency = currency;
        p.method = method;
        p.createdAt = now;
        switch (result) {
            case AuthorizationResult.Approved a -> {
                p.status = PaymentStatus.AUTHORIZED;
                p.providerReference = a.providerReference();
            }
            case AuthorizationResult.Declined d -> {
                p.status = PaymentStatus.DECLINED;
                p.declineReason = d.reason();
            }
        }
        return p;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public String getProviderReference() { return providerReference; }
    public String getDeclineReason() { return declineReason; }
    public Instant getCreatedAt() { return createdAt; }
}
