package com.vipinkumar.orderflow.order.app;

import com.vipinkumar.orderflow.order.api.CreateOrderRequest;
import com.vipinkumar.orderflow.order.domain.IdempotencyRecord;
import com.vipinkumar.orderflow.order.domain.IdempotencyRepository;
import com.vipinkumar.orderflow.order.domain.Order;
import com.vipinkumar.orderflow.order.domain.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orders;
    @Mock IdempotencyRepository idempotency;
    @Mock OrderEvents events;
    @Mock PlatformTransactionManager txManager;

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private OrderService service;

    private final CreateOrderRequest request = new CreateOrderRequest("cust-1", "CARD",
            List.of(new CreateOrderRequest.Line("CHARGER-35W", 1), new CreateOrderRequest.Line("CHARGER-35W", 2)));

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, idempotency, events, new TransactionTemplate(txManager), clock,
                new SimpleMeterRegistry());
    }

    @Test
    void firstRequestCreatesOrderAndStartsSagaInOneTransaction() {
        when(idempotency.findById("key-12345")).thenReturn(Optional.empty());

        PlacedOrder placed = service.place(request, "key-12345");

        assertThat(placed.replayed()).isFalse();
        // duplicate SKUs in the request are merged into one line
        assertThat(placed.order().getLines()).singleElement()
                .satisfies(l -> assertThat(l.getQuantity()).isEqualTo(3));
        verify(orders).save(placed.order());
        verify(idempotency).saveAndFlush(argThat(r -> r.getOrderId().equals(placed.order().getId())));
        verify(events).statusChanged(placed.order(), null);
        verify(events).reserveInventory(placed.order());
    }

    @Test
    void retryWithSameKeyAndBodyReturnsTheOriginalOrder() {
        Order original = Order.place("cust-1", "CARD", Map.of("CHARGER-35W", 3), clock.instant());
        when(idempotency.findById("key-12345")).thenReturn(Optional.of(
                new IdempotencyRecord("key-12345", RequestFingerprint.of(request), original.getId(), clock.instant())));
        when(orders.findWithLinesById(original.getId())).thenReturn(Optional.of(original));

        PlacedOrder placed = service.place(request, "key-12345");

        assertThat(placed.replayed()).isTrue();
        assertThat(placed.order()).isSameAs(original);
        verify(orders, never()).save(any());
        verify(events, never()).reserveInventory(any());
    }

    @Test
    void sameKeyWithDifferentBodyIsRefused() {
        when(idempotency.findById("key-12345")).thenReturn(Optional.of(
                new IdempotencyRecord("key-12345", "something-else", java.util.UUID.randomUUID(), clock.instant())));

        assertThatThrownBy(() -> service.place(request, "key-12345"))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void concurrentDuplicateLosesTheRaceAndGetsTheWinnersOrder() {
        Order winner = Order.place("cust-1", "CARD", Map.of("CHARGER-35W", 3), clock.instant());
        when(idempotency.findById("key-12345")).thenReturn(
                Optional.empty(),
                Optional.of(new IdempotencyRecord("key-12345", RequestFingerprint.of(request), winner.getId(), clock.instant())));
        when(idempotency.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(orders.findWithLinesById(winner.getId())).thenReturn(Optional.of(winner));

        PlacedOrder placed = service.place(request, "key-12345");

        assertThat(placed.replayed()).isTrue();
        assertThat(placed.order()).isSameAs(winner);
    }
}
