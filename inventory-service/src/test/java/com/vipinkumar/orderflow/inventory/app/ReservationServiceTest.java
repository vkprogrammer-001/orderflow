package com.vipinkumar.orderflow.inventory.app;

import com.vipinkumar.orderflow.events.EventCodec;
import com.vipinkumar.orderflow.events.EventEnvelope;
import com.vipinkumar.orderflow.events.EventTypes;
import com.vipinkumar.orderflow.events.Payloads;
import com.vipinkumar.orderflow.inventory.domain.Product;
import com.vipinkumar.orderflow.inventory.domain.ProductRepository;
import com.vipinkumar.orderflow.inventory.domain.Reservation;
import com.vipinkumar.orderflow.inventory.domain.ReservationRepository;
import com.vipinkumar.orderflow.inventory.domain.ReservationStatus;
import com.vipinkumar.orderflow.outbox.InboxGuard;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationServiceTest {

    @Mock ProductRepository products;
    @Mock ReservationRepository reservations;
    @Mock InventoryEvents events;
    @Mock InboxGuard inbox;
    @Mock PlatformTransactionManager txManager;

    private final EventCodec codec = new EventCodec("test");
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private ReservationService service;
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReservationService(products, reservations, events, inbox, codec,
                new TransactionTemplate(txManager), new ConcurrentMapCacheManager(ProductCatalog.CACHE), clock,
                new SimpleMeterRegistry());
        when(inbox.firstDelivery(anyString(), any(UUID.class))).thenReturn(true);
        when(reservations.findById(orderId)).thenReturn(Optional.empty());
        when(reservations.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(products.findAllById(any())).thenReturn(List.of(
                new Product("A-SKU", "A", new BigDecimal("10.00"), "USD", 5, clock.instant()),
                new Product("B-SKU", "B", new BigDecimal("2.50"), "USD", 5, clock.instant())));
    }

    private EventEnvelope reserve(Payloads.Line... lines) {
        return codec.wrap(EventTypes.RESERVE_INVENTORY, orderId.toString(),
                new Payloads.ReserveInventory(orderId, List.of(lines)), null);
    }

    @Test
    void holdsEveryLineInSkuOrderAndRepliesWithPrices() {
        when(products.tryReserve(anyString(), anyInt())).thenReturn(1);

        service.handle(reserve(new Payloads.Line("B-SKU", 1), new Payloads.Line("A-SKU", 2)));

        InOrder lockOrder = inOrder(products);
        lockOrder.verify(products).tryReserve("A-SKU", 2);
        lockOrder.verify(products).tryReserve("B-SKU", 1);

        var saved = ArgumentCaptor.forClass(Reservation.class);
        verify(reservations).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(saved.getValue().getLines()).extracting(Reservation.HeldLine::getUnitPrice)
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("2.50"));
        verify(events).reserved(saved.getValue(), "USD", orderId.toString());
    }

    @Test
    void insufficientStockRecordsARejectionInAFreshTransaction() {
        when(products.tryReserve("A-SKU", 2)).thenReturn(1);
        when(products.tryReserve("B-SKU", 9)).thenReturn(0);

        service.handle(reserve(new Payloads.Line("A-SKU", 2), new Payloads.Line("B-SKU", 9)));

        verify(txManager).rollback(any()); // the partial A-SKU decrement is undone
        var saved = ArgumentCaptor.forClass(Reservation.class);
        verify(reservations).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ReservationStatus.REJECTED);
        verify(events).rejected(eq(orderId), eq("B-SKU"), anyString(), eq(orderId.toString()));
        verify(events, never()).reserved(any(), any(), any());
    }

    @Test
    void unknownSkuIsRejected() {
        service.handle(reserve(new Payloads.Line("NOPE", 1)));

        verify(products, never()).tryReserve(anyString(), anyInt());
        verify(events).rejected(eq(orderId), eq("NOPE"), anyString(), any());
    }

    @Test
    void releaseBeforeReserveLeavesATombstoneThatBlocksTheLateReserve() {
        service.handle(codec.wrap(EventTypes.RELEASE_INVENTORY, orderId.toString(),
                new Payloads.ReleaseInventory(orderId, "timeout"), null));

        var tombstone = ArgumentCaptor.forClass(Reservation.class);
        verify(reservations).save(tombstone.capture());
        assertThat(tombstone.getValue().getStatus()).isEqualTo(ReservationStatus.RELEASED);
        verify(events).released(orderId, orderId.toString());

        when(reservations.findById(orderId)).thenReturn(Optional.of(tombstone.getValue()));
        service.handle(reserve(new Payloads.Line("A-SKU", 1)));

        verify(products, never()).tryReserve(anyString(), anyInt());
        verify(events).rejected(eq(orderId), any(), eq("Order was already cancelled"), any());
    }

    @Test
    void releasingAHeldReservationReturnsStock() {
        Reservation held = Reservation.held(orderId,
                List.of(new Reservation.HeldLine("A-SKU", 2, BigDecimal.TEN)), clock.instant());
        when(reservations.findById(orderId)).thenReturn(Optional.of(held));
        when(products.release("A-SKU", 2)).thenReturn(1);

        service.handle(codec.wrap(EventTypes.RELEASE_INVENTORY, orderId.toString(),
                new Payloads.ReleaseInventory(orderId, "declined"), null));

        verify(products).release("A-SKU", 2);
        assertThat(held.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        verify(events).released(orderId, orderId.toString());
    }

    @Test
    void duplicateCommandDoesNothing() {
        when(inbox.firstDelivery(anyString(), any(UUID.class))).thenReturn(false);

        service.handle(reserve(new Payloads.Line("A-SKU", 1)));

        verify(products, never()).tryReserve(anyString(), anyInt());
        verify(reservations, never()).save(any());
    }
}
