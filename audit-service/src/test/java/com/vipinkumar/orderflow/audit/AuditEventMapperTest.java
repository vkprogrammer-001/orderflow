package com.vipinkumar.orderflow.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vipinkumar.orderflow.events.EventCodec;
import com.vipinkumar.orderflow.events.EventTypes;
import com.vipinkumar.orderflow.events.Payloads;
import com.vipinkumar.orderflow.events.Topics;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventMapperTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private final AuditEventMapper mapper = new AuditEventMapper(new ObjectMapper(), clock);
    private final EventCodec codec = new EventCodec("order-service");

    @Test
    void keepsIdentityCorrelationAndPayload() {
        UUID orderId = UUID.randomUUID();
        var event = codec.wrap(EventTypes.ORDER_STATUS_CHANGED, orderId.toString(),
                new Payloads.OrderStatusChanged(orderId, "PENDING", "RESERVED", "Stock held"), null);

        AuditEvent doc = mapper.toDocument(event, Topics.ORDER_EVENTS);

        assertThat(doc.eventId()).isEqualTo(event.eventId().toString()); // _id => natural de-duplication
        assertThat(doc.aggregateId()).isEqualTo(orderId.toString());
        assertThat(doc.topic()).isEqualTo(Topics.ORDER_EVENTS);
        assertThat(doc.receivedAt()).isEqualTo(clock.instant());
        assertThat(doc.payload()).containsEntry("to", "RESERVED").containsEntry("reason", "Stock held");
    }
}
