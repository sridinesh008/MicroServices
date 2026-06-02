package com.ratelimiter.limiter;

import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;

/**
 * Public API for rate limiting. Sits above the store layer.
 * Callers (HTTP filter, gRPC interceptor, etc.) use this interface.
 * The store handles persistence; this layer handles algorithm selection.
 */
public interface RateLimiter {

    /**
     * Check whether the request identified by key is within the limit defined by rule.
     * Consumes one token on allow; does not consume on deny.
     */
    RateLimitResult checkLimit(RateLimitKey key, RateLimitRule rule);

    /** Clears all stored state for the given client (admin / unblock use case). */
    void reset(RateLimitKey key);

    /**
     * Returns true when the backing store is reachable.
     * Callers can fail-open or fail-closed based on this flag.
     */
    boolean isAvailable();
}
