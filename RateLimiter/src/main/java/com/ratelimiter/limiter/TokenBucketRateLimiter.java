package com.ratelimiter.limiter;

import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.store.RateLimitStore;

/**
 * Token bucket implementation of RateLimiter.
 *
 * This class is a thin coordination layer:
 *   - it holds the store reference
 *   - it calls tryConsumeTokens (cost=1 per request)
 *   - it delegates health and reset to the store
 *
 * All thread safety is guaranteed by RateLimitStore.tryConsumeTokens
 * (implemented via ConcurrentHashMap.compute in InMemoryRateLimitStore,
 *  and Lua scripts in RedisRateLimitStore in Phase 6).
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final RateLimitStore store;

    public TokenBucketRateLimiter(RateLimitStore store) {
        this.store = store;
    }

    /**
     * Each HTTP request costs 1 token.
     * Passes cost=1 to the store; the store handles refill and atomicity.
     */
    @Override
    public RateLimitResult checkLimit(RateLimitKey key, RateLimitRule rule) {
        return store.tryConsumeTokens(key, rule, 1);
    }

    @Override
    public void reset(RateLimitKey key) {
        store.reset(key);
    }

    /** Propagates store health — false means Redis is down (Phase 6). */
    @Override
    public boolean isAvailable() {
        return store.isHealthy();
    }
}
