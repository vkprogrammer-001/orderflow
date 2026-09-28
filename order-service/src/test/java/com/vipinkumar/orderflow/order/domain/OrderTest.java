package com.vipinkumar.orderflow.order.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    private Order twoLineOrder() {
        Map<String, Integer> lines = new LinkedHashMap<>();
        lines.put("CHARGER-35W", 2);
        lines.put("BRAID-CABLE-1M", 1);
        return Order.place("cust-1", "CARD", lines, now);
    }

    @Test
    void newOrderIsPendingWithoutATotal() {
        Order order = twoLineOrder();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isNull();
        assertThat(order.getLines()).hasSize(2);
    }

    @Test
    void totalComesFromCataloguePrices() {
        Order order = twoLineOrder();
        order.applyPrices(Map.of("CHARGER-35W", new BigDecimal("39.00"), "BRAID-CABLE-1M", new BigDecimal("19.10")), "USD");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("97.10");
        assertThat(order.getCurrency()).isEqualTo("USD");
    }

    @Test
    void refusesToPriceWhenASkuIsMissing() {
        Order order = twoLineOrder();
        assertThatThrownBy(() -> order.applyPrices(Map.of("CHARGER-35W", BigDecimal.ONE), "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void happyPathTransitions() {
        Order order = twoLineOrder();
        assertThat(order.transitionTo(OrderStatus.RESERVED, null, now)).isEqualTo(OrderStatus.PENDING);
        assertThat(order.transitionTo(OrderStatus.CONFIRMED, null, now)).isEqualTo(OrderStatus.RESERVED);
        assertThat(order.getStatus().isTerminal()).isTrue();
    }

    @Test
    void cannotSkipOrReverseStates() {
        Order order = twoLineOrder();
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.CONFIRMED, null, now))
                .isInstanceOf(IllegalTransitionException.class);
        order.transitionTo(OrderStatus.REJECTED, "out of stock", now);
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.RESERVED, null, now))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CONFIRMED", "REJECTED", "CANCELLED"})
    void terminalStatesAllowNothing(OrderStatus terminal) {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(terminal.canTransitionTo(target)).isFalse();
        }
    }
}
