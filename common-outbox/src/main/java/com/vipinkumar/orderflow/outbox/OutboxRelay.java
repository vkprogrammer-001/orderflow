package com.vipinkumar.orderflow.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Polls unpublished outbox rows and publishes them to Kafka.
 *
 * <p>{@code FOR UPDATE SKIP LOCKED} lets several replicas run the relay concurrently without
 * double-sending or blocking each other: each replica claims a disjoint batch. Delivery is
 * at-least-once (a crash after send but before commit re-sends), which is why every consumer
 * de-duplicates with {@link InboxGuard}.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final int batchSize;
    private final Counter published;
    private final Counter failed;

    public OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate tx,
                       int batchSize, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.tx = tx;
        this.batchSize = batchSize;
        this.published = meters.counter("outbox.published");
        this.failed = meters.counter("outbox.failed");
    }

    private record Row(UUID id, String topic, String key, String payload) {}

    @Scheduled(fixedDelayString = "${orderflow.outbox.poll-interval-ms:200}")
    public void relay() {
        Integer sent;
        do {
            sent = tx.execute(status -> publishBatch());
        } while (sent != null && sent == batchSize); // drain quickly under load
    }

    int publishBatch() {
        List<Row> rows = jdbc.query("""
                SELECT id, topic, message_key, payload::text AS payload
                FROM outbox_events
                WHERE published_at IS NULL
                ORDER BY created_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("topic"),
                        rs.getString("message_key"), rs.getString("payload")),
                batchSize);
        if (rows.isEmpty()) {
            return 0;
        }

        List<CompletableFuture<SendResult<String, String>>> futures = new ArrayList<>(rows.size());
        for (Row row : rows) {
            futures.add(kafka.send(row.topic(), row.key(), row.payload()));
        }

        List<UUID> done = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            try {
                futures.get(i).get(10, TimeUnit.SECONDS);
                done.add(row.id());
            } catch (Exception e) {
                failed.increment();
                log.warn("Outbox publish failed for {} on {}: {}", row.id(), row.topic(), e.getMessage());
                jdbc.update("UPDATE outbox_events SET attempts = attempts + 1, last_error = ? WHERE id = ?",
                        truncate(e.getMessage()), row.id());
            }
        }

        if (!done.isEmpty()) {
            jdbc.batchUpdate("UPDATE outbox_events SET published_at = now() WHERE id = ?",
                    done, done.size(), (ps, id) -> ps.setObject(1, id));
            published.increment(done.size());
        }
        // Report what was actually sent: if Kafka is down, the drain loop in relay() stops and
        // waits for the next poll instead of spinning on the same failing rows.
        return done.size();
    }

    /** Keeps the table small; published rows are only useful for short-term debugging. */
    @Scheduled(cron = "${orderflow.outbox.cleanup-cron:0 0 * * * *}")
    public void purgePublished() {
        int removed = jdbc.update("DELETE FROM outbox_events WHERE published_at < now() - interval '7 days'");
        if (removed > 0) {
            log.info("Purged {} published outbox rows", removed);
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
