package com.vipinkumar.orderflow.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Wire format for every message on the bus.
 *
 * <p>{@code eventId} is globally unique and is what consumers de-duplicate on (Kafka is at-least-once).
 * {@code aggregateId} is used as the Kafka partition key so all events for one order stay ordered.
 * {@code correlationId} ties every hop of one saga together for tracing and audit.
 */
public record EventEnvelope(
        UUID eventId,
        String type,
        String aggregateId,
        Instant occurredAt,
        String correlationId,
        String source,
        JsonNode payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(payload, "payload");
    }
}
