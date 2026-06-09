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

    /**
     * Secret for the X-Admin-Key header on /api/v1/admin/** endpoints.
     * Blank = auth disabled (dev convenience). Set via ADMIN_KEY env var in prod.
     */
    private String adminKey = "";

    public StoreType getStoreType() { return storeType; }
    public void setStoreType(StoreType storeType) { this.storeType = storeType; }

    public String getAdminKey() { return adminKey; }
    public void setAdminKey(String adminKey) { this.adminKey = adminKey; }
}
