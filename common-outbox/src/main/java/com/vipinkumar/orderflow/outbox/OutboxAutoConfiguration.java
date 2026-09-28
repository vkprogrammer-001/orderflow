package com.vipinkumar.orderflow.outbox;

import com.vipinkumar.orderflow.events.EventCodec;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Registers the outbox writer, relay and inbox guard for any service that has this jar on its classpath. */
@AutoConfiguration(after = {HibernateJpaAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
        KafkaAutoConfiguration.class})
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventCodec eventCodec(@Value("${spring.application.name}") String serviceName) {
        return new EventCodec(serviceName);
    }

    @Bean
    public OutboxWriter outboxWriter(JdbcTemplate jdbc, EventCodec codec) {
        return new OutboxWriter(jdbc, codec);
    }

    @Bean
    public InboxGuard inboxGuard(JdbcTemplate jdbc) {
        return new InboxGuard(jdbc);
    }

    @Bean
    @ConditionalOnProperty(name = "orderflow.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
    public OutboxRelay outboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
                                   PlatformTransactionManager txManager,
                                   @Value("${orderflow.outbox.batch-size:100}") int batchSize,
                                   ObjectProvider<MeterRegistry> meters) {
        return new OutboxRelay(jdbc, kafka, new TransactionTemplate(txManager), batchSize,
                meters.getIfAvailable(SimpleMeterRegistry::new));
    }

    /**
     * Retries a failing record with exponential back-off, then parks it on {@code <topic>.DLT}
     * so one poison message can't block its partition. Malformed JSON is never retried.
     */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        ExponentialBackOff backOff = new ExponentialBackOff(200, 2.0);
        backOff.setMaxElapsedTime(5_000);
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafka), backOff);
        handler.addNotRetryableExceptions(EventCodec.MalformedEventException.class);
        return handler;
    }
}
