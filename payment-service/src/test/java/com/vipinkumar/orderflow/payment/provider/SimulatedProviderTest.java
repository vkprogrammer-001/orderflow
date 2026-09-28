package com.vipinkumar.orderflow.payment.provider;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedProviderTest {

    private static PaymentRequest request(String amount) {
        return new PaymentRequest("order-1", "cust-1", new BigDecimal(amount), "USD", "CARD");
    }

    @Test
    void approvesWithinLimit() {
        var card = new SimulatedProvider.Card(new BigDecimal("100"), 0.5, () -> 0.99);
        assertThat(card.authorize(request("99.99")))
                .isInstanceOfSatisfying(AuthorizationResult.Approved.class,
                        a -> assertThat(a.providerReference()).startsWith("card-"));
    }

    @Test
    void declinesOverLimitWithAReadableReason() {
        var card = new SimulatedProvider.Card(new BigDecimal("100"), 0, () -> 0.99);
        assertThat(card.authorize(request("100.01")))
                .isInstanceOfSatisfying(AuthorizationResult.Declined.class,
                        d -> assertThat(d.reason()).contains("CARD limit is 100"));
    }

    @Test
    void declinesNonPositiveAmounts() {
        var card = new SimulatedProvider.Card(new BigDecimal("100"), 0, () -> 0.99);
        assertThat(card.authorize(request("0"))).isInstanceOf(AuthorizationResult.Declined.class);
    }

    @Test
    void transientFailuresAreExceptionsSoTheyCanBeRetried() {
        var flaky = new SimulatedProvider.Card(new BigDecimal("100"), 0.5, () -> 0.1);
        assertThatThrownBy(() -> flaky.authorize(request("10"))).isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void registryPicksTheStrategyByMethodAndRejectsDuplicates() {
        var card = new SimulatedProvider.Card(BigDecimal.TEN, 0);
        var upi = new SimulatedProvider.Upi(BigDecimal.TEN, 0);
        var registry = new PaymentProviderRegistry(List.of(card, upi));

        assertThat(registry.forMethod("UPI")).containsSame(upi);
        assertThat(registry.forMethod("CRYPTO")).isEmpty();
        assertThatThrownBy(() -> new PaymentProviderRegistry(List.of(card, card)))
                .isInstanceOf(IllegalStateException.class);
    }
}
