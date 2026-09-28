package com.vipinkumar.orderflow.inventory.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** One row per order: what inventory holds for it, and at which prices. */
@Entity
@Table(name = "reservations")
public class Reservation implements Persistable<UUID> {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @Column(length = 255)
    private String reason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reservation_lines", joinColumns = @JoinColumn(name = "order_id"))
    private List<HeldLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Assigned ids: tell Spring Data this is new so save() issues one INSERT instead of SELECT + INSERT. */
    @Transient
    private boolean isNew;

    protected Reservation() {}

    private Reservation(UUID orderId, ReservationStatus status, String reason, List<HeldLine> lines, Instant now) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
        this.lines = new ArrayList<>(lines);
        this.createdAt = now;
        this.updatedAt = now;
        this.isNew = true;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() { return orderId; }

    @Override
    public boolean isNew() { return isNew; }

    public static Reservation held(UUID orderId, List<HeldLine> lines, Instant now) {
        return new Reservation(orderId, ReservationStatus.HELD, null, lines, now);
    }

    public static Reservation rejected(UUID orderId, String reason, Instant now) {
        return new Reservation(orderId, ReservationStatus.REJECTED, reason, List.of(), now);
    }

    public static Reservation releasedTombstone(UUID orderId, String reason, Instant now) {
        return new Reservation(orderId, ReservationStatus.RELEASED, reason, List.of(), now);
    }

    public void markReleased(String reason, Instant now) {
        this.status = ReservationStatus.RELEASED;
        this.reason = reason;
        this.updatedAt = now;
    }

    public UUID getOrderId() { return orderId; }
    public ReservationStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public List<HeldLine> getLines() { return Collections.unmodifiableList(lines); }

    @Embeddable
    public static class HeldLine {
        @Column(nullable = false, length = 64)
        private String sku;
        @Column(nullable = false)
        private int quantity;
        @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
        private BigDecimal unitPrice;

        protected HeldLine() {}

        public HeldLine(String sku, int quantity, BigDecimal unitPrice) {
            this.sku = sku;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public String getSku() { return sku; }
        public int getQuantity() { return quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
    }
}
