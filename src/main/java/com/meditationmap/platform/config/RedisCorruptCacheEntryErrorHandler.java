package com.meditationmap.platform.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Redis 에 GenericJackson2JsonRedisSerializer 가 기대하지 않는 형식(예전 포맷·손상 데이터)이 남아 있으면
 * 역직렬화 시 {@link JsonProcessingException}(하위에 {@code MismatchedInputException} 포함)이 납니다.
 *
 * <p>기본 {@link org.springframework.cache.interceptor.SimpleCacheErrorHandler} 는 예외를 그대로 던져 GET API 가
 * 실패합니다. Spring Cache 는 핸들러가 예외를 삼키면 캐시 미스로 간주하고 {@code valueLoader} 로 본문을 다시
 * 불러옵니다({@code AbstractCacheInvoker#doGet} 참고). 여기서 깨진 키를 evict 해 다음 put 이 정상 포맷이
 * 되도록 합니다.
 */
@Slf4j
public class RedisCorruptCacheEntryErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        if (isRedisJacksonDeserializationFailure(exception)) {
            log.warn(
                    "Redis 캐시 역직렬화 실패 → 항목 삭제 후 원본 조회로 복구합니다. cache={} key={}",
                    cache.getName(),
                    key,
                    exception);
            try {
                cache.evict(key);
            } catch (RuntimeException evictFail) {
                log.debug("손상 캐시 evict 실패(무시 가능)", evictFail);
            }
            return;
        }
        throw exception;
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        throw exception;
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        throw exception;
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        throw exception;
    }

    private static boolean isRedisJacksonDeserializationFailure(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof JsonProcessingException) {
                return true;
            }
        }
        return false;
    }
}
