package com.vipinkumar.orderflow.events;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventCodecTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
    private final EventCodec codec = new EventCodec("order-service", clock);

    @Test
    void roundTripsEnvelopeAndPayload() {
        UUID orderId = UUID.randomUUID();
        var cmd = new Payloads.ReserveInventory(orderId, List.of(new Payloads.Line("SKU-1", 2)));

        EventEnvelope env = codec.wrap(EventTypes.RESERVE_INVENTORY, orderId.toString(), cmd, "corr-1");
        EventEnvelope parsed = codec.fromJson(codec.toJson(env));

        assertThat(parsed.eventId()).isEqualTo(env.eventId());
        assertThat(parsed.occurredAt()).isEqualTo(clock.instant());
        assertThat(parsed.correlationId()).isEqualTo("corr-1");
        assertThat(codec.payload(parsed, Payloads.ReserveInventory.class)).isEqualTo(cmd);
    }

    @Test
    void keepsMoneyExactIncludingScale() {
        UUID orderId = UUID.randomUUID();
        var reply = new Payloads.InventoryReserved(orderId,
                List.of(new Payloads.PricedLine("SKU-1", 3, new BigDecimal("19.10"))), "USD");

        var parsed = codec.fromJson(codec.toJson(codec.wrap(EventTypes.INVENTORY_RESERVED, orderId.toString(), reply, null)));

        // A double round-trip would give 19.1 (and eventually 0.1 + 0.2 problems); records compare BigDecimal with scale.
        assertThat(codec.payload(parsed, Payloads.InventoryReserved.class)).isEqualTo(reply);
    }

    @Test
    void correlationDefaultsToAggregateId() {
        var env = codec.wrap(EventTypes.AUTHORIZE_PAYMENT, "agg-1",
                new Payloads.AuthorizePayment(UUID.randomUUID(), "c", BigDecimal.TEN, "INR", "CARD"), null);
        assertThat(env.correlationId()).isEqualTo("agg-1");
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> codec.fromJson("{not json"))
                .isInstanceOf(EventCodec.MalformedEventException.class);
    }
}
