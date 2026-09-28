package com.vipinkumar.orderflow.audit;

import com.vipinkumar.orderflow.events.EventCodec;
import com.vipinkumar.orderflow.events.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Taps every saga topic with its own consumer group, so auditing never slows the saga down. */
@Component
public class AuditListener {

    private final AuditEventRepository repository;
    private final AuditEventMapper mapper;
    private final EventCodec codec;

    public AuditListener(AuditEventRepository repository, AuditEventMapper mapper, EventCodec codec) {
        this.repository = repository;
        this.mapper = mapper;
        this.codec = codec;
    }

    @KafkaListener(groupId = "audit", topics = {
            Topics.INVENTORY_COMMANDS, Topics.PAYMENT_COMMANDS,
            Topics.INVENTORY_EVENTS, Topics.PAYMENT_EVENTS, Topics.ORDER_EVENTS})
    public void onEvent(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        repository.save(mapper.toDocument(codec.fromJson(message), topic));
    }
}
