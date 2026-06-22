package com.meditationmap.platform.config;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

@Configuration
public class ApplicationCachingConfigurer implements CachingConfigurer {

    @Override
    @Nullable
    public CacheErrorHandler errorHandler() {
        return new RedisCorruptCacheEntryErrorHandler();
    }
}
