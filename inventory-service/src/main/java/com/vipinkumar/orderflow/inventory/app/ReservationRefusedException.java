package com.vipinkumar.orderflow.inventory.app;

/** Thrown inside the reservation transaction to roll back every partial decrement. */
public class ReservationRefusedException extends RuntimeException {
    private final String sku;

    public ReservationRefusedException(String sku, String message) {
        super(message);
        this.sku = sku;
    }

    public String sku() {
        return sku;
    }
}
