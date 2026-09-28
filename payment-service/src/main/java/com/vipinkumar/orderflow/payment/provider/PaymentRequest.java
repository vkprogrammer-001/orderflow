package com.vipinkumar.orderflow.payment.provider;

import java.math.BigDecimal;

/**
 * What we send to a provider. {@code idempotencyKey} (the order id) lets the provider de-duplicate
 * our retries, which is how real card/UPI gateways make "retry after timeout" safe.
 */
public record PaymentRequest(String idempotencyKey, String customerId, BigDecimal amount, String currency, String method) {}
