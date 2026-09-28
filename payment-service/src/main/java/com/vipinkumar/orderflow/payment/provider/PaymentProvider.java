package com.vipinkumar.orderflow.payment.provider;

/** Strategy: one implementation per payment method. Adding a method means adding a class, not an if-branch. */
public interface PaymentProvider {

    /** The payment method this provider handles, e.g. {@code CARD}. */
    String method();

    AuthorizationResult authorize(PaymentRequest request);
}
