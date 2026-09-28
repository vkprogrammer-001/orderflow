package com.vipinkumar.orderflow.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vipinkumar.orderflow.events.EventEnvelope;

import java.time.Clock;
import java.util.Map;

/** Converts a wire envelope into a stored document. Pure function, easy to unit test. */
public class AuditEventMapper {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final ObjectMapper mapper;
    private final Clock clock;

    public AuditEventMapper(ObjectMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public AuditEvent toDocument(EventEnvelope e, String topic) {
        Map<String, Object> payload = mapper.convertValue(e.payload(), MAP);
        return new AuditEvent(e.eventId().toString(), e.type(), e.aggregateId(), e.correlationId(), e.source(),
                topic, e.occurredAt(), clock.instant(), payload);
    }
}
