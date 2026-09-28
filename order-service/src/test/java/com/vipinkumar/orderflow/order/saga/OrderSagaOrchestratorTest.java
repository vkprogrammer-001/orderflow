package com.vipinkumar.orderflow.order.saga;

import com.vipinkumar.orderflow.events.EventCodec;
import com.vipinkumar.orderflow.events.EventEnvelope;
import com.vipinkumar.orderflow.events.EventTypes;
import com.vipinkumar.orderflow.events.Payloads;
import com.vipinkumar.orderflow.order.app.OrderEvents;
import com.vipinkumar.orderflow.order.domain.Order;
import com.vipinkumar.orderflow.order.domain.OrderRepository;
import com.vipinkumar.orderflow.order.domain.OrderStatus;
import com.vipinkumar.orderflow.outbox.InboxGuard;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    @Mock OrderRepository orders;
    @Mock OrderEvents events;
    @Mock InboxGuard inbox;

    private final EventCodec codec = new EventCodec("test");
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private OrderSagaOrchestrator saga;
    private Order order;

    @BeforeEach
    void setUp() {
        saga = new OrderSagaOrchestrator(orders, events, inbox, codec, clock, new SimpleMeterRegistry());
        order = Order.place("cust-1", "CARD", Map.of("CHARGER-35W", 2), clock.instant());
    }

    private EventEnvelope reply(String type, Object payload) {
        return codec.wrap(type, order.getId().toString(), payload, null);
    }

    private void firstDelivery() {
        when(inbox.firstDelivery(eq(OrderSagaOrchestrator.CONSUMER), any(UUID.class))).thenReturn(true);
        when(orders.findWithLinesById(order.getId())).thenReturn(Optional.of(order));
    }

    @Test
    void reservedStockPricesTheOrderAndRequestsPayment() {
        firstDelivery();

        saga.handle(reply(EventTypes.INVENTORY_RESERVED, new Payloads.InventoryReserved(order.getId(),
                List.of(new Payloads.PricedLine("CHARGER-35W", 2, new BigDecimal("39.00"))), "USD")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RESERVED);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("78.00");
        verify(events).statusChanged(order, OrderStatus.PENDING);
        verify(events).authorizePayment(order);
    }

    @Test
    void declinedPaymentCompensatesByReleasingStock() {
        firstDelivery();
        order.transitionTo(OrderStatus.RESERVED, null, clock.instant());

        saga.handle(reply(EventTypes.PAYMENT_DECLINED, new Payloads.PaymentDeclined(order.getId(), "Card limit")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLING);
        verify(events).releaseInventory(order, "Card limit");
    }

    @Test
    void releaseCompletesTheCancellation() {
        firstDelivery();
        order.transitionTo(OrderStatus.RESERVED, null, clock.instant());
        order.transitionTo(OrderStatus.CANCELLING, "Card limit", clock.instant());

        saga.handle(reply(EventTypes.INVENTORY_RELEASED, new Payloads.InventoryReleased(order.getId())));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getStatusReason()).isEqualTo("Card limit");
    }

    @Test
    void authorisedPaymentConfirms() {
        firstDelivery();
        order.transitionTo(OrderStatus.RESERVED, null, clock.instant());
        UUID paymentId = UUID.randomUUID();

        saga.handle(reply(EventTypes.PAYMENT_AUTHORIZED, new Payloads.PaymentAuthorized(order.getId(), paymentId, "card-1")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentId()).isEqualTo(paymentId);
    }

    @Test
    void duplicateDeliveryIsANoOp() {
        when(inbox.firstDelivery(anyString(), any(UUID.class))).thenReturn(false);

        saga.handle(reply(EventTypes.INVENTORY_REJECTED, new Payloads.InventoryRejected(order.getId(), "X", "none left")));

        verifyNoInteractions(orders, events);
    }

    @Test
    void lateReplyCannotResurrectARejectedOrder() {
        firstDelivery();
        order.transitionTo(OrderStatus.REJECTED, "timeout", clock.instant());

        saga.handle(reply(EventTypes.INVENTORY_RESERVED, new Payloads.InventoryReserved(order.getId(),
                List.of(new Payloads.PricedLine("CHARGER-35W", 2, BigDecimal.TEN)), "USD")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        verify(events, never()).authorizePayment(any());
    }
}
