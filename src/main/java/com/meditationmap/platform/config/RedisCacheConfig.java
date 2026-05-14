package com.meditationmap.platform.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
@Profile("!local")
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${app.cache.default-ttl-seconds:600}") long defaultTtlSeconds) {
        RedisCacheConfiguration defaults =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofSeconds(defaultTtlSeconds))
                        .disableCachingNullValues()
                        .serializeKeysWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        RedisSerializer.string()))
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        RedisSerializer.json()));

        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        perCache.put("regions", defaults.entryTtl(Duration.ofSeconds(defaultTtlSeconds)));
        perCache.put("places", defaults.entryTtl(Duration.ofSeconds(defaultTtlSeconds)));
        perCache.put("place", defaults.entryTtl(Duration.ofSeconds(defaultTtlSeconds)));
        perCache.put("experts", defaults.entryTtl(Duration.ofSeconds(defaultTtlSeconds)));
        perCache.put("expert", defaults.entryTtl(Duration.ofSeconds(defaultTtlSeconds)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                .transactionAware()
                .build();
    }
}
