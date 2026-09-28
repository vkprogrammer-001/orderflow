package com.vipinkumar.orderflow.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Builds, serialises and parses {@link EventEnvelope}s. Thread-safe. */
public final class EventCodec {

    private final ObjectMapper mapper;
    private final Clock clock;
    private final String source;

    public EventCodec(String source) {
        this(source, Clock.systemUTC());
    }

    public EventCodec(String source, Clock clock) {
        this.source = source;
        this.clock = clock;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Money must never pass through a double: keep decimals exact (and their scale) end to end.
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false);
    }

    public EventEnvelope wrap(String type, String aggregateId, Object payload, String correlationId) {
        return new EventEnvelope(UUID.randomUUID(), type, aggregateId, Instant.now(clock),
                correlationId == null ? aggregateId : correlationId, source, mapper.valueToTree(payload));
    }

    public String toJson(EventEnvelope envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise event " + envelope.eventId(), e);
        }
    }

    public EventEnvelope fromJson(String json) {
        try {
            return mapper.readValue(json, EventEnvelope.class);
        } catch (JsonProcessingException e) {
            throw new MalformedEventException("Could not parse event", e);
        }
    }

    public <T> T payload(EventEnvelope envelope, Class<T> type) {
        try {
            return mapper.treeToValue(envelope.payload(), type);
        } catch (JsonProcessingException e) {
            throw new MalformedEventException("Payload of " + envelope.eventId() + " is not a " + type.getSimpleName(), e);
        }
    }

    public String source() {
        return source;
    }

    public static class MalformedEventException extends RuntimeException {
        public MalformedEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
