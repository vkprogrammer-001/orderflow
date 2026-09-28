package com.vipinkumar.orderflow.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Idempotent-consumer guard. Kafka delivers at-least-once, so a handler must be safe to run twice.
 *
 * <p>Call {@link #firstDelivery} inside the same transaction as the handler's side effects. The
 * insert is keyed on (consumer, event_id): a duplicate is detected by {@code ON CONFLICT DO NOTHING},
 * and if two deliveries race, one commits and the other's transaction sees zero rows inserted.
 */
public class InboxGuard {

    private final JdbcTemplate jdbc;

    public InboxGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(String consumer, UUID eventId) {
        int inserted = jdbc.update("""
                INSERT INTO processed_messages (consumer, event_id, processed_at)
                VALUES (?, ?, now())
                ON CONFLICT DO NOTHING
                """, consumer, eventId);
        return inserted == 1;
    }
}
