package com.vipinkumar.orderflow.payment.config;

import com.vipinkumar.orderflow.payment.provider.PaymentProvider;
import com.vipinkumar.orderflow.payment.provider.PaymentProviderRegistry;
import com.vipinkumar.orderflow.payment.provider.SimulatedProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

/** Providers are plain Java (no Spring annotations) and wired here, which keeps them trivially unit-testable. */
@Configuration
public class PaymentConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PaymentProvider cardProvider(@Value("${orderflow.providers.card.limit:2500}") BigDecimal limit,
                                 @Value("${orderflow.providers.failure-rate:0.05}") double failureRate) {
        return new SimulatedProvider.Card(limit, failureRate);
    }

    @Bean
    PaymentProvider upiProvider(@Value("${orderflow.providers.upi.limit:1000}") BigDecimal limit,
                                @Value("${orderflow.providers.failure-rate:0.05}") double failureRate) {
        return new SimulatedProvider.Upi(limit, failureRate);
    }

    @Bean
    PaymentProvider walletProvider(@Value("${orderflow.providers.wallet.limit:300}") BigDecimal limit,
                                   @Value("${orderflow.providers.failure-rate:0.05}") double failureRate) {
        return new SimulatedProvider.Wallet(limit, failureRate);
    }

    @Bean
    PaymentProviderRegistry paymentProviderRegistry(List<PaymentProvider> providers) {
        return new PaymentProviderRegistry(providers);
    }
}
