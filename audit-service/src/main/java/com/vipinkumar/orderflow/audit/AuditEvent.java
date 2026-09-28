package com.vipinkumar.orderflow.audit;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * One immutable document per message seen on the bus. {@code _id} is the event id, so a
 * redelivered message overwrites itself instead of creating a duplicate: idempotent by construction.
 */
@Document("events")
@CompoundIndex(name = "aggregate_time", def = "{'aggregateId': 1, 'occurredAt': 1}")
public record AuditEvent(
        @Id String eventId,
        String type,
        String aggregateId,
        String correlationId,
        String source,
        String topic,
        @Indexed Instant occurredAt,
        Instant receivedAt,
        Map<String, Object> payload) {}
