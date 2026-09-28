package com.vipinkumar.orderflow.payment.provider;

/** Transient failure (timeout, 5xx). Retried, and counted by the circuit breaker. */
public class ProviderUnavailableException extends RuntimeException {
    public ProviderUnavailableException(String message) {
        super(message);
    }
}
