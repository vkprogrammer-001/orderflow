package com.vipinkumar.orderflow.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Remembers which order an {@code Idempotency-Key} produced, plus a hash of the request body,
 * so a client retry returns the same order and a reused key with a different body is refused.
 *
 * <p>Implements {@link Persistable} so a new record is always INSERTed. Without it Spring Data sees an
 * assigned id, calls {@code merge()}, and a racing duplicate would silently UPDATE the winner's row
 * instead of hitting the primary key and failing, which is the signal the service relies on.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord implements Persistable<String> {

    @Id
    @Column(name = "idem_key", length = 100)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(String key, String requestHash, UUID orderId, Instant createdAt) {
        this.key = key;
        this.requestHash = requestHash;
        this.orderId = orderId;
        this.createdAt = createdAt;
        this.isNew = true;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public String getId() { return key; }

    @Override
    public boolean isNew() { return isNew; }

    public String getKey() { return key; }
    public String getRequestHash() { return requestHash; }
    public UUID getOrderId() { return orderId; }
}
