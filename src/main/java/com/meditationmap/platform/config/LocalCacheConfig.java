package com.meditationmap.platform.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 로컬 프로필에서는 Redis 캐시 대신 인메모리 캐시를 사용합니다.
 * Redis에 예전 직렬화 포맷으로 남은 키가 있으면 역직렬화 예외로 GET /places·/experts 등이 500이 될 수 있어,
 * 로컬 개발 시 Redis 캐시 의존을 제거합니다.
 */
@Configuration
@Profile("local")
public class LocalCacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("regions", "places", "place", "experts", "expert");
    }
}
