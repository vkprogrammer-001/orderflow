package com.vipinkumar.orderflow.order.config;

import com.vipinkumar.orderflow.events.Topics;
import com.vipinkumar.orderflow.order.security.ApiKeyFilter;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.time.Clock;

@Configuration
public class OrderServiceConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(@Value("${orderflow.security.api-key}") String apiKey) {
        var registration = new FilterRegistrationBean<>(new ApiKeyFilter(apiKey));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    /**
     * Order service owns topic creation for the saga. Partitioning by order id means per-order
     * ordering while different orders are processed in parallel across partitions.
     */
    @Bean
    KafkaAdmin.NewTopics sagaTopics(@Value("${orderflow.kafka.partitions:6}") int partitions,
                                    @Value("${orderflow.kafka.replicas:1}") short replicas) {
        return new KafkaAdmin.NewTopics(
                topic(Topics.INVENTORY_COMMANDS, partitions, replicas),
                topic(Topics.PAYMENT_COMMANDS, partitions, replicas),
                topic(Topics.INVENTORY_EVENTS, partitions, replicas),
                topic(Topics.PAYMENT_EVENTS, partitions, replicas),
                topic(Topics.ORDER_EVENTS, partitions, replicas));
    }

    private static NewTopic topic(String name, int partitions, short replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}
