package com.vipinkumar.orderflow.payment.app;

import com.vipinkumar.orderflow.payment.provider.AuthorizationResult;
import com.vipinkumar.orderflow.payment.provider.PaymentProvider;
import com.vipinkumar.orderflow.payment.provider.PaymentProviderRegistry;
import com.vipinkumar.orderflow.payment.provider.PaymentRequest;
import com.vipinkumar.orderflow.payment.provider.ProviderUnavailableException;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves the aspect ordering claim in {@link PaymentGateway}: transient failures really are retried. */
@SpringBootTest(classes = {PaymentGateway.class, PaymentGatewayResilienceTest.Providers.class}, properties = {
        "resilience4j.retry.instances.provider.max-attempts=3",
        "resilience4j.retry.instances.provider.wait-duration=1ms",
        "resilience4j.retry.instances.provider.retry-exceptions=com.vipinkumar.orderflow.payment.provider.ProviderUnavailableException",
        "resilience4j.circuitbreaker.instances.provider.minimum-number-of-calls=100"
})
@ImportAutoConfiguration({AopAutoConfiguration.class, RetryAutoConfiguration.class, CircuitBreakerAutoConfiguration.class})
class PaymentGatewayResilienceTest {

    static final AtomicInteger calls = new AtomicInteger();

    @TestConfiguration
    static class Providers {
        /** Fails twice, then succeeds. */
        @Bean
        PaymentProviderRegistry registry() {
            PaymentProvider flaky = new PaymentProvider() {
                @Override public String method() { return "CARD"; }
                @Override public AuthorizationResult authorize(PaymentRequest r) {
                    if (calls.incrementAndGet() <= 2) throw new ProviderUnavailableException("timeout");
                    return new AuthorizationResult.Approved("card-ok");
                }
            };
            PaymentProvider down = new PaymentProvider() {
                @Override public String method() { return "UPI"; }
                @Override public AuthorizationResult authorize(PaymentRequest r) {
                    throw new ProviderUnavailableException("down");
                }
            };
            return new PaymentProviderRegistry(List.of(flaky, down));
        }
    }

    @Autowired PaymentGateway gateway;

    @Test
    void retriesTransientFailuresThenSucceeds() {
        calls.set(0);
        var result = gateway.authorize(new PaymentRequest("o-1", "c", BigDecimal.TEN, "USD", "CARD"));
        assertThat(result).isEqualTo(new AuthorizationResult.Approved("card-ok"));
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void declinesGracefullyWhenTheProviderStaysDown() {
        var result = gateway.authorize(new PaymentRequest("o-2", "c", BigDecimal.TEN, "USD", "UPI"));
        assertThat(result).isInstanceOf(AuthorizationResult.Declined.class);
    }
}
