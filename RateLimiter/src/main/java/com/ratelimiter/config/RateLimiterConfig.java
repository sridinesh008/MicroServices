package com.ratelimiter.config;

import com.ratelimiter.limiter.RateLimiter;
import com.ratelimiter.limiter.TokenBucketRateLimiter;
import com.ratelimiter.store.InMemoryRateLimitStore;
import com.ratelimiter.store.RateLimitStore;
import com.ratelimiter.store.RedisRateLimitStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Wires the correct RateLimitStore based on rate-limiter.store-type in application.yml.
 *
 * IN_MEMORY → InMemoryRateLimitStore  (default, no external deps)
 * REDIS     → RedisRateLimitStore     (Phase 5, requires Redis)
 */
@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RateLimiterConfig {

    private final RateLimiterProperties properties;

    public RateLimiterConfig(RateLimiterProperties properties) {
        this.properties = properties;
    }

    /**
     * StringRedisTemplate is auto-configured by Spring Boot when
     * spring-boot-starter-data-redis is on the classpath.
     * Lettuce (the default driver) connects lazily — no Redis connection
     * is opened at startup when store-type=in-memory.
     */
    @Bean
    public RateLimitStore rateLimitStore(StringRedisTemplate redisTemplate) {
        return switch (properties.getStoreType()) {
            case IN_MEMORY -> new InMemoryRateLimitStore();
            case REDIS     -> new RedisRateLimitStore(redisTemplate);
        };
    }

    @Bean
    public RateLimiter rateLimiter(RateLimitStore store) {
        return new TokenBucketRateLimiter(store);
    }
}
