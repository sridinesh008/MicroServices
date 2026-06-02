package com.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds all "rate-limiter.*" keys from application.yml.
 * Spring Boot relaxed binding: "in-memory" → IN_MEMORY, "redis" → REDIS.
 */
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    /** Which backing store to use. Defaults to IN_MEMORY (safe for single-node dev). */
    private StoreType storeType = StoreType.IN_MEMORY;

    public StoreType getStoreType() {
        return storeType;
    }

    public void setStoreType(StoreType storeType) {
        this.storeType = storeType;
    }
}
