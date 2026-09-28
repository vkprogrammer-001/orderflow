package com.vipinkumar.orderflow.inventory.domain;

public enum ReservationStatus {
    /** Stock is held for the order. */
    HELD,
    /** Stock was returned (or, if nothing was ever held, this is a tombstone that blocks a late reserve). */
    RELEASED,
    /** Reservation was refused; nothing is held. */
    REJECTED
}
