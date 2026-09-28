package com.vipinkumar.orderflow.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vipinkumar.orderflow.events.EventCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Clock;

@Configuration
public class AuditConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    EventCodec eventCodec(@Value("${spring.application.name}") String name) {
        return new EventCodec(name);
    }

    @Bean
    AuditEventMapper auditEventMapper(ObjectMapper mapper, Clock clock) {
        return new AuditEventMapper(mapper, clock);
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafka), new FixedBackOff(500, 3));
        handler.addNotRetryableExceptions(EventCodec.MalformedEventException.class);
        return handler;
    }
}
