package com.ratelimiter.config;

import com.ratelimiter.limiter.RateLimiter;
import com.ratelimiter.limiter.TokenBucketRateLimiter;
import com.ratelimiter.store.FallbackRateLimitStore;
import com.ratelimiter.store.InMemoryRateLimitStore;
import com.ratelimiter.store.RateLimitStore;
import com.ratelimiter.store.RedisRateLimitStore;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RateLimiterConfig {

    /**
     * Active when rate-limiter.store-type=in-memory (or property absent).
     * StringRedisTemplate is NOT injected here — no Redis connection opened at startup.
     * Example: dev machine with no Redis → app starts clean.
     */
    @Bean
    @ConditionalOnProperty(
        name = "rate-limiter.store-type",
        havingValue = "in-memory",
        matchIfMissing = true   // safe default: in-memory when property absent
    )
    public RateLimitStore inMemoryRateLimitStore() {
        return new InMemoryRateLimitStore();
    }

    /**
     * Active only when rate-limiter.store-type=redis.
     * Wraps RedisRateLimitStore in FallbackRateLimitStore (circuit breaker).
     * Example: failureThreshold=3, recoveryMs=5000 →
     *   3 Redis failures → OPEN → InMemory fallback; probe every 5s → CLOSED when Redis back.
     */
    /**
     * Active only when rate-limiter.store-type=redis.
     * Wraps RedisRateLimitStore in FallbackRateLimitStore (Resilience4j circuit breaker).
     * Circuit breaker "redis-store" config comes from application-prod.yml resilience4j section.
     * Example: window=10, threshold=50%, wait=5s → 5/10 failures → OPEN → InMemory fallback.
     */
    @Bean
    @ConditionalOnProperty(name = "rate-limiter.store-type", havingValue = "redis")
    public RateLimitStore redisRateLimitStore(StringRedisTemplate redisTemplate,
                                              CircuitBreakerRegistry cbRegistry) {
        RedisRateLimitStore redis = new RedisRateLimitStore(redisTemplate);
        InMemoryRateLimitStore fallback = new InMemoryRateLimitStore();
        return new FallbackRateLimitStore(redis, fallback, cbRegistry.circuitBreaker("redis-store"));
    }

    @Bean
    public RateLimiter rateLimiter(RateLimitStore store) {
        return new TokenBucketRateLimiter(store);
    }
}
