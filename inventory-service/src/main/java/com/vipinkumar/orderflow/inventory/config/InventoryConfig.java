package com.vipinkumar.orderflow.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vipinkumar.orderflow.inventory.app.ProductCatalog;
import com.vipinkumar.orderflow.inventory.app.ProductView;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class InventoryConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Typed JSON serializer for the product cache (no Java serialization, no polymorphic type info
     * in Redis), a short TTL as a safety net, and transaction-aware eviction so a rolled-back
     * reservation never evicts or poisons the cache.
     */
    @Bean
    RedisCacheManagerBuilderCustomizer productCache(ObjectMapper mapper) {
        var serializer = new Jackson2JsonRedisSerializer<>(mapper, ProductView.class);
        return builder -> builder
                .transactionAware()
                .withCacheConfiguration(ProductCatalog.CACHE, RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofSeconds(60))
                        .disableCachingNullValues()
                        .prefixCacheNameWith("inventory:")
                        .serializeValuesWith(SerializationPair.fromSerializer(serializer)));
    }
}
