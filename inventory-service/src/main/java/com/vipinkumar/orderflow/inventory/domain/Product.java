package com.vipinkumar.orderflow.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @Column(length = 64)
    private String sku;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Units that can still be sold. */
    @Column(nullable = false)
    private int available;

    /** Units held for orders whose payment hasn't settled yet. */
    @Column(nullable = false)
    private int reserved;

    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {}

    public Product(String sku, String name, BigDecimal unitPrice, String currency, int available, Instant now) {
        this.sku = sku;
        this.name = name;
        this.unitPrice = unitPrice;
        this.currency = currency;
        this.available = available;
        this.updatedAt = now;
    }

    public void restock(int newAvailable, Instant now) {
        if (newAvailable < 0) throw new IllegalArgumentException("Stock cannot be negative");
        this.available = newAvailable;
        this.updatedAt = now;
    }

    public String getSku() { return sku; }
    public String getName() { return name; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getCurrency() { return currency; }
    public int getAvailable() { return available; }
    public int getReserved() { return reserved; }
}
