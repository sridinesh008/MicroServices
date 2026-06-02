package com.ratelimiter.store;

import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;

public interface RateLimitStore {

    RateLimitResult tryConsumeTokens(RateLimitKey key, RateLimitRule rule, int tokens);

    void reset(RateLimitKey key);

    boolean isHealthy();
}
