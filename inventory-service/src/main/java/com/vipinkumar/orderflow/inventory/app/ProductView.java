package com.vipinkumar.orderflow.inventory.app;

import com.vipinkumar.orderflow.inventory.domain.Product;

import java.math.BigDecimal;

/** Cache- and API-friendly snapshot of a product (entities never leave the service layer). */
public record ProductView(String sku, String name, BigDecimal unitPrice, String currency, int available, int reserved) {
    public static ProductView from(Product p) {
        return new ProductView(p.getSku(), p.getName(), p.getUnitPrice(), p.getCurrency(), p.getAvailable(), p.getReserved());
    }
}
