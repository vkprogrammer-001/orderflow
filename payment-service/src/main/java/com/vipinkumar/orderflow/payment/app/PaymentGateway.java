package com.vipinkumar.orderflow.payment.app;

import com.vipinkumar.orderflow.payment.provider.AuthorizationResult;
import com.vipinkumar.orderflow.payment.provider.PaymentProviderRegistry;
import com.vipinkumar.orderflow.payment.provider.PaymentRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resilient call into the payment provider.
 *
 * <p>Resilience4j nests aspects as Retry( CircuitBreaker( call ) ). The fallback sits on the
 * <b>outer</b> Retry on purpose: if it sat on the circuit breaker, every failure would be turned
 * into a result before Retry ever saw an exception, and nothing would be retried. Here transient
 * errors are retried with back-off, the breaker opens when the provider is unhealthy (so we stop
 * hammering it), and only when all of that fails do we decline.
 */
@Component
public class PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PaymentGateway.class);

    private final PaymentProviderRegistry registry;

    public PaymentGateway(PaymentProviderRegistry registry) {
        this.registry = registry;
    }

    @Retry(name = "provider", fallbackMethod = "unavailable")
    @CircuitBreaker(name = "provider")
    public AuthorizationResult authorize(PaymentRequest request) {
        return registry.forMethod(request.method())
                .map(provider -> provider.authorize(request))
                .orElseGet(() -> new AuthorizationResult.Declined("Unsupported payment method " + request.method()));
    }

    @SuppressWarnings("unused") // invoked reflectively by Resilience4j
    private AuthorizationResult unavailable(PaymentRequest request, Throwable cause) {
        log.warn("Provider for {} unavailable after retries: {}", request.method(), cause.toString());
        return new AuthorizationResult.Declined("Payment provider unavailable, please try again");
    }
}
