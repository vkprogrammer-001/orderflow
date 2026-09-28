package com.vipinkumar.orderflow.payment.provider;

/** Business outcome of an authorisation. Infrastructure failures are exceptions, not results. */
public sealed interface AuthorizationResult {

    record Approved(String providerReference) implements AuthorizationResult {}

    record Declined(String reason) implements AuthorizationResult {}
}
