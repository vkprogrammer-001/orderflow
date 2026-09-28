package com.vipinkumar.orderflow.payment.app;

import com.vipinkumar.orderflow.events.EventCodec;
import com.vipinkumar.orderflow.events.EventEnvelope;
import com.vipinkumar.orderflow.events.EventTypes;
import com.vipinkumar.orderflow.events.Payloads;
import com.vipinkumar.orderflow.outbox.InboxGuard;
import com.vipinkumar.orderflow.payment.domain.Payment;
import com.vipinkumar.orderflow.payment.domain.PaymentRepository;
import com.vipinkumar.orderflow.payment.domain.PaymentStatus;
import com.vipinkumar.orderflow.payment.provider.AuthorizationResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository payments;
    @Mock PaymentGateway gateway;
    @Mock PaymentEvents events;
    @Mock InboxGuard inbox;
    @Mock PlatformTransactionManager txManager;

    private final EventCodec codec = new EventCodec("test");
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final UUID orderId = UUID.randomUUID();
    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, gateway, events, inbox, codec, new TransactionTemplate(txManager),
                clock, new SimpleMeterRegistry());
    }

    private EventEnvelope authorize() {
        return codec.wrap(EventTypes.AUTHORIZE_PAYMENT, orderId.toString(),
                new Payloads.AuthorizePayment(orderId, "cust-1", new BigDecimal("78.00"), "USD", "CARD"), null);
    }

    @Test
    void chargesOnceAndRepliesWithTheOutcome() {
        when(payments.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(gateway.authorize(any())).thenReturn(new AuthorizationResult.Approved("card-abc"));
        when(inbox.firstDelivery(anyString(), any(UUID.class))).thenReturn(true);
        when(payments.saveAndFlush(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handle(authorize());

        var saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(saved.getValue().getProviderReference()).isEqualTo("card-abc");
        verify(events).outcome(saved.getValue(), orderId.toString());
    }

    @Test
    void redeliveredCommandReplaysTheStoredAnswerWithoutCallingTheProvider() {
        Payment existing = Payment.record(orderId, "cust-1", BigDecimal.TEN, "USD", "CARD",
                new AuthorizationResult.Declined("limit"), clock.instant());
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(existing));
        when(inbox.firstDelivery(anyString(), any(UUID.class))).thenReturn(true);

        service.handle(authorize());

        verifyNoInteractions(gateway);
        verify(events).outcome(existing, orderId.toString());
    }

    @Test
    void losingAConcurrentRaceIsSwallowed() {
        when(payments.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(gateway.authorize(any())).thenReturn(new AuthorizationResult.Approved("card-abc"));
        when(inbox.firstDelivery(anyString(), any(UUID.class))).thenReturn(true);
        when(payments.saveAndFlush(any(Payment.class))).thenThrow(new DataIntegrityViolationException("order_id"));

        assertThatCode(() -> service.handle(authorize())).doesNotThrowAnyException();
        verify(events, never()).outcome(any(), any());
    }

    @Test
    void ignoresOtherMessageTypes() {
        service.handle(codec.wrap(EventTypes.RESERVE_INVENTORY, orderId.toString(),
                new Payloads.InventoryReleased(orderId), null));
        verifyNoInteractions(payments, gateway, events);
    }
}
