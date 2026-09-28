package com.vipinkumar.orderflow.payment.provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Factory/registry that picks the strategy for a payment method. Fails fast on duplicate registrations. */
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> byMethod;

    public PaymentProviderRegistry(List<PaymentProvider> providers) {
        this.byMethod = providers.stream().collect(Collectors.toUnmodifiableMap(
                PaymentProvider::method, Function.identity(),
                (a, b) -> { throw new IllegalStateException("Two providers registered for " + a.method()); }));
    }

    public Optional<PaymentProvider> forMethod(String method) {
        return Optional.ofNullable(byMethod.get(method));
    }
}
