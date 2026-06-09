package com.ratelimiter.store;

import java.util.concurrent.ConcurrentHashMap;

import com.ratelimiter.model.AlgorithmType;
import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;

/**
 * Single-node, in-memory implementation of RateLimitStore.
 * Supports TOKEN_BUCKET and FIXED_WINDOW algorithms.
 * Used in dev/test. Replaced by RedisRateLimitStore in production.
 *
 * Thread safety: ConcurrentHashMap.compute() locks only the affected key's bucket,
 * so multiple threads on different keys run fully in parallel.
 */
public class InMemoryRateLimitStore implements RateLimitStore {

    /**
     * Token bucket state for one (ruleId + clientId + endpoint) combination.
     * tokens       — how many tokens are currently available (fractional during refill)
     * lastRefillMs — timestamp of last refill
     */
    private static class BucketState {
        double tokens;
        long lastRefillMs;

        BucketState(double tokens, long lastRefillMs) {
            this.tokens = tokens;
            this.lastRefillMs = lastRefillMs;
        }
    }

    // Key format: "ruleId|scope|clientId|endpoint"
    private final ConcurrentHashMap<String, BucketState> tokenBuckets  = new ConcurrentHashMap<>();
    // Fixed window state: long[0] = windowId, long[1] = count
    private final ConcurrentHashMap<String, long[]>      windowCounters = new ConcurrentHashMap<>();

    @Override
    public RateLimitResult tryConsumeTokens(RateLimitKey key, RateLimitRule rule, int tokens) {
        String storeKey = buildKey(key, rule);
        return switch (rule.algorithmType()) {
            case FIXED_WINDOW -> fixedWindow(storeKey, rule, tokens);
            default           -> tokenBucket(storeKey, rule, tokens);
        };
    }

    /**
     * Token bucket — lazy refill strategy.
     *
     * Every request calculates elapsed time since last refill, adds earned tokens,
     * then checks if there are enough to serve the cost.
     *
     * Formula:
     *   elapsed   = (now - lastRefillMs) / 1000.0
     *   newTokens = min(capacity, stored + elapsed * rate)
     *   if newTokens >= cost → allow, deduct cost
     *   else                 → deny, return retryAfterSeconds
     */
    private RateLimitResult tokenBucket(String storeKey, RateLimitRule rule, int tokens) {
        RateLimitResult[] result = new RateLimitResult[1];

        tokenBuckets.compute(storeKey, (k, bucket) -> {
            long now = System.currentTimeMillis();

            if (bucket == null) {
                bucket = new BucketState(rule.capacity(), now);
            }

            double elapsed   = (now - bucket.lastRefillMs) / 1000.0;
            double newTokens = Math.min(rule.capacity(), bucket.tokens + elapsed * rule.refillRatePerSecond());

            if (newTokens >= tokens) {
                newTokens -= tokens;
                bucket.tokens = newTokens;
                bucket.lastRefillMs = now;
                long resetAt = now / 1000 + (long) Math.ceil((rule.capacity() - newTokens) / rule.refillRatePerSecond());
                result[0] = new RateLimitResult(true, (long) newTokens, rule.capacity(), resetAt, 0L);
            } else {
                long retryAfter = (long) Math.ceil((tokens - newTokens) / rule.refillRatePerSecond());
                result[0] = new RateLimitResult(false, (long) newTokens, rule.capacity(), now / 1000 + retryAfter, retryAfter);
            }

            return bucket;
        });

        return result[0];
    }

    /**
     * Fixed window — count requests within a rolling time window.
     *
     * Window size = 1000 / refillRatePerSecond milliseconds.
     * E.g. refillRate=1.0 → 1-second window; refillRate=0.5 → 2-second window.
     * A new window resets the counter. Requests within capacity are allowed.
     */
    private RateLimitResult fixedWindow(String storeKey, RateLimitRule rule, int cost) {
        RateLimitResult[] result = new RateLimitResult[1];
        long now          = System.currentTimeMillis();
        long windowSizeMs = Math.max(1L, (long)(1000.0 / rule.refillRatePerSecond()));
        long windowId     = now / windowSizeMs;
        long windowEndMs  = (windowId + 1) * windowSizeMs;

        windowCounters.compute(storeKey, (k, state) -> {
            if (state == null || state[0] != windowId) {
                state = new long[]{windowId, 0};
            }
            long count = state[1];
            if (count + cost <= rule.capacity()) {
                state[1] = count + cost;
                long remaining = rule.capacity() - state[1];
                result[0] = new RateLimitResult(true, remaining, rule.capacity(), windowEndMs / 1000, 0L);
            } else {
                long retryAfterMs  = windowEndMs - now;
                long retryAfterSec = Math.max(1L, (retryAfterMs + 999) / 1000);
                result[0] = new RateLimitResult(false, 0L, rule.capacity(), windowEndMs / 1000, retryAfterSec);
            }
            return state;
        });

        return result[0];
    }

    /**
     * Clears all rate limit state for the given client across both algorithms.
     * Key format is "ruleId|scope|clientId|endpoint".
     * Matching on "|clientId|" avoids false matches on other segments.
     */
    @Override
    public void reset(RateLimitKey key) {
        String clientSegment = "|" + key.clientId() + "|";
        tokenBuckets.keySet().removeIf(k -> k.contains(clientSegment));
        windowCounters.keySet().removeIf(k -> k.contains(clientSegment));
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    /** Key format: "ruleId|scope|clientId|endpoint" */
    private String buildKey(RateLimitKey key, RateLimitRule rule) {
        return rule.ruleId() + "|" + key.scope() + "|" + key.clientId() + "|" + key.endpoint();
    }
}
