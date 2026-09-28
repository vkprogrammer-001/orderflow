package com.vipinkumar.orderflow.order;

import com.vipinkumar.orderflow.events.EventCodec;
import com.vipinkumar.orderflow.events.EventEnvelope;
import com.vipinkumar.orderflow.events.EventTypes;
import com.vipinkumar.orderflow.events.Payloads;
import com.vipinkumar.orderflow.events.Topics;
import com.vipinkumar.orderflow.order.api.OrderResponse;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Runs the real order service against real Postgres and Kafka, plays the part of the inventory and
 * payment services by hand, and checks the saga ends in CONFIRMED. Requires Docker: {@code mvn verify -Pit}.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "orderflow.security.api-key=integration-test-key-123")
class OrderSagaIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired TestRestTemplate http;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;

    private final EventCodec codec = new EventCodec("test-participant");

    @Test
    void orderIsConfirmedAfterStockAndPaymentReplies() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", "integration-test-key-123");
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        var body = Map.of("customerId", "cust-it", "paymentMethod", "CARD",
                "lines", List.of(Map.of("sku", "CHARGER-35W", "quantity", 2)));

        var created = http.postForEntity("/api/v1/orders", new HttpEntity<>(body, headers), OrderResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID orderId = created.getBody().id();

        // The outbox relay must publish the ReserveInventory command for this order.
        EventEnvelope command = awaitCommand(Topics.INVENTORY_COMMANDS, orderId);
        assertThat(command.type()).isEqualTo(EventTypes.RESERVE_INVENTORY);

        send(Topics.INVENTORY_EVENTS, orderId, EventTypes.INVENTORY_RESERVED, new Payloads.InventoryReserved(orderId,
                List.of(new Payloads.PricedLine("CHARGER-35W", 2, new BigDecimal("39.00"))), "USD"));

        EventEnvelope pay = awaitCommand(Topics.PAYMENT_COMMANDS, orderId);
        assertThat(codec.payload(pay, Payloads.AuthorizePayment.class).amount()).isEqualByComparingTo("78.00");

        send(Topics.PAYMENT_EVENTS, orderId, EventTypes.PAYMENT_AUTHORIZED,
                new Payloads.PaymentAuthorized(orderId, UUID.randomUUID(), "card-it"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var order = http.exchange("/api/v1/orders/" + orderId, org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers), OrderResponse.class).getBody();
            assertThat(order.status()).isEqualTo("CONFIRMED");
        });
    }

    private void send(String topic, UUID orderId, String type, Object payload) {
        kafkaTemplate.send(topic, orderId.toString(),
                codec.toJson(codec.wrap(type, orderId.toString(), payload, orderId.toString())));
    }

    private EventEnvelope awaitCommand(String topic, UUID orderId) {
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.key().equals(orderId.toString())) {
                        return codec.fromJson(record.value());
                    }
                }
            }
        }
        throw new AssertionError("No message for " + orderId + " on " + topic);
    }
}
