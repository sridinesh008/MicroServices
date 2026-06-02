package com.ratelimiter.config;

import com.ratelimiter.limiter.RateLimiter;
import com.ratelimiter.limiter.TokenBucketRateLimiter;
import com.ratelimiter.store.InMemoryRateLimitStore;
import com.ratelimiter.store.RateLimitStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RateLimiterConfigTest {

    @Autowired
    RateLimiterProperties properties;

    @Autowired
    RateLimitStore store;

    @Autowired
    RateLimiter rateLimiter;

    /**
     * application.yml sets rate-limiter.store-type: in-memory.
     * Spring Boot's relaxed binding converts "in-memory" → StoreType.IN_MEMORY.
     */
    @Test
    void defaultStoreTypeBindsFromYaml() {
        assertEquals(StoreType.IN_MEMORY, properties.getStoreType());
    }

    /**
     * When storeType=IN_MEMORY, the RateLimitStore bean must be InMemoryRateLimitStore.
     * This is the gate: if someone misconfigures the switch, this test fails immediately.
     */
    @Test
    void inMemoryStoreTypeProvidesInMemoryBean() {
        assertInstanceOf(InMemoryRateLimitStore.class, store);
    }

    /**
     * The RateLimiter bean must be TokenBucketRateLimiter (only algorithm we support).
     */
    @Test
    void rateLimiterBeanIsTokenBucket() {
        assertInstanceOf(TokenBucketRateLimiter.class, rateLimiter);
    }
}
