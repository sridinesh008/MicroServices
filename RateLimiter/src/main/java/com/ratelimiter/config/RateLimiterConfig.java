package com.ratelimiter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.limiter.RateLimiter;
import com.ratelimiter.limiter.TokenBucketRateLimiter;
import com.ratelimiter.rule.CachedRuleRepository;
import com.ratelimiter.rule.InMemoryRuleRepository;
import com.ratelimiter.rule.RateLimitRuleRepository;
import com.ratelimiter.rule.RedisRuleRepository;
import com.ratelimiter.store.FallbackRateLimitStore;
import com.ratelimiter.store.InMemoryRateLimitStore;
import com.ratelimiter.store.RateLimitStore;
import com.ratelimiter.store.RedisRateLimitStore;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RateLimiterConfig {

    // ── Rate-limit stores ────────────────────────────────────────────────────

    /** In-memory store: no Redis dependency; safe default for dev. */
    @Bean
    @ConditionalOnProperty(
        name = "rate-limiter.store-type",
        havingValue = "in-memory",
        matchIfMissing = true
    )
    public RateLimitStore inMemoryRateLimitStore() {
        return new InMemoryRateLimitStore();
    }

    /**
     * Redis store wrapped in Resilience4j circuit breaker.
     * Circuit breaker config: application-prod.yml resilience4j section.
     * Failure path: window=10, threshold=50% → OPEN → InMemory fallback for wait-duration.
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

    // ── Rule repositories ────────────────────────────────────────────────────

    /** In-memory rule repo: rules live only for the lifetime of the process. */
    @Bean
    @ConditionalOnProperty(
        name = "rate-limiter.store-type",
        havingValue = "in-memory",
        matchIfMissing = true
    )
    public RateLimitRuleRepository inMemoryRuleRepository() {
        return new InMemoryRuleRepository();
    }

    /**
     * Redis-backed rule repository with local cache.
     *
     * findAll() is served from ConcurrentHashMap — 0 extra Redis calls on the hot path.
     * Writes go to Redis immediately and update the local map.
     * Cross-instance sync: pub/sub on rl:rules:events triggers reload() on all instances.
     */
    @Bean
    @ConditionalOnProperty(name = "rate-limiter.store-type", havingValue = "redis")
    public CachedRuleRepository ruleRepository(StringRedisTemplate redisTemplate,
                                               ObjectMapper objectMapper,
                                               RedisConnectionFactory connectionFactory) {
        return new CachedRuleRepository(
            new RedisRuleRepository(redisTemplate, objectMapper),
            connectionFactory
        );
    }
}
