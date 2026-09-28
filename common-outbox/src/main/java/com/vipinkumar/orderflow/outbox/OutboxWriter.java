package com.vipinkumar.orderflow.outbox;

import com.vipinkumar.orderflow.events.EventCodec;
import com.vipinkumar.orderflow.events.EventEnvelope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

/**
 * Writes an event to the {@code outbox_events} table <b>in the caller's transaction</b>.
 *
 * <p>This is the whole point of the pattern: the business row and the event are committed atomically,
 * so we never publish an event for a change that rolled back, and never lose an event for a change that
 * committed. {@link OutboxRelay} ships the rows to Kafka afterwards.
 */
public class OutboxWriter {

    private final JdbcTemplate jdbc;
    private final EventCodec codec;

    public OutboxWriter(JdbcTemplate jdbc, EventCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, EventEnvelope event) {
        jdbc.update("""
                INSERT INTO outbox_events (id, topic, message_key, event_type, payload, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?)
                """,
                event.eventId(), topic, event.aggregateId(), event.type(),
                codec.toJson(event), Timestamp.from(event.occurredAt()));
    }
}
