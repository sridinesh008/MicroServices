package com.ratelimiter.store;

import java.util.concurrent.ConcurrentHashMap;

import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;

/**
 * Single-node, in-memory implementation of RateLimitStore.
 * Used in dev/test. Replaced by RedisRateLimitStore in production (Phase 6).
 *
 * Thread safety: ConcurrentHashMap.compute() locks only the affected key's bucket,
 * so multiple threads on different keys run fully in parallel.
 */
public class InMemoryRateLimitStore implements RateLimitStore {

    /**
     * Holds the live state of one token bucket.
     * One BucketState exists per (ruleId + clientId + endpoint) combination.
     *
     * tokens       — how many tokens are currently available (can be fractional during refill)
     * lastRefillMs — the timestamp of the last refill, used to calculate elapsed time
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
    private final ConcurrentHashMap<String, BucketState> tokenBuckets = new ConcurrentHashMap<>();

    /**
     * Token bucket algorithm — lazy refill strategy.
     *
     * "Lazy" means we do NOT refill on a background timer.
     * Instead, every time a request arrives we calculate how much time
     * has passed since the last refill, compute how many tokens were
     * earned in that time, and add them before checking if we can serve.
     *
     * Formula:
     *   elapsed   = (now - lastRefillMs) / 1000.0          ← seconds since last check
     *   newTokens = min(capacity, stored + elapsed * rate)  ← add earned tokens, cap at max
     *   if newTokens >= 1 → allow, deduct 1
     *   else              → deny,  tell client when to retry
     *
     * Concrete example (capacity=5, refillRate=1 token/sec):
     *   Bucket had 1 token. Last refill was 3 seconds ago.
     *   elapsed   = (now - lastRefillMs) / 1000 = 3.0 sec
     *   newTokens = min(5, 1 + 3.0 × 1.0)       = min(5, 4.0) = 4.0
     *   4.0 >= 1 (cost) → ALLOWED. remaining = 4.0 - 1 = 3.0
     *
     * Deny example (capacity=5, refillRate=1 token/sec):
     *   Bucket has 0 tokens. Request arrives immediately (elapsed ≈ 0).
     *   newTokens = min(5, 0 + 0 × 1.0) = 0.0
     *   0.0 < 1 (cost) → DENIED.
     *   retryAfter = ceil((1 - 0.0) / 1.0) = ceil(1.0) = 1 second
     */
    @Override
    public RateLimitResult tryConsumeTokens(RateLimitKey key, RateLimitRule rule, int tokens) {
        String storeKey = buildKey(key, rule);

        // result[] is a 1-element array so the lambda can write to it.
        // Java lambdas cannot assign to a plain local variable from an outer scope.
        RateLimitResult[] result = new RateLimitResult[1];

        // compute() locks only this key's entry in the map.
        // The entire lambda runs atomically — no other thread can touch this bucket
        // between the read (bucket) and the write (return bucket).
        // This is what makes the store thread-safe without a global lock.
        tokenBuckets.compute(storeKey, (k, bucket) -> {
            long now = System.currentTimeMillis();

            // First request for this key: start the bucket full
            if (bucket == null) {
                bucket = new BucketState(rule.capacity(), now);
            }

            // How many seconds passed since we last touched this bucket?
            // Example: now=5000ms, lastRefillMs=2000ms → elapsed = (5000-2000)/1000 = 3.0 sec
            double elapsed = (now - bucket.lastRefillMs) / 1000.0;

            // Add tokens earned during elapsed time, but cap at max capacity.
            // Example: stored=1.0, elapsed=3.0, rate=1.0 → 1.0 + 3.0 = 4.0 (under cap of 5)
            double newTokens = Math.min(rule.capacity(), bucket.tokens + elapsed * rule.refillRatePerSecond());

            if (newTokens >= tokens) {
                // Enough tokens → allow the request, deduct the cost
                newTokens -= tokens;
                bucket.tokens = newTokens;
                bucket.lastRefillMs = now;

                // resetAt = seconds until bucket is full again.
                // Example: capacity=5, newTokens=3.0, rate=1.0 → ceil((5-3)/1) = 2 seconds from now
                long resetAt = now / 1000 + (long) Math.ceil((rule.capacity() - newTokens) / rule.refillRatePerSecond());
                result[0] = new RateLimitResult(true, (long) newTokens, rule.capacity(), resetAt, 0L);
            } else {
                // Not enough tokens → deny. Tell the client how long to wait.
                // Example: need 1 token, have 0.2 → ceil((1 - 0.2) / 1.0) = ceil(0.8) = 1 second
                long retryAfter = (long) Math.ceil((tokens - newTokens) / rule.refillRatePerSecond());
                result[0] = new RateLimitResult(false, (long) newTokens, rule.capacity(), now / 1000 + retryAfter, retryAfter);
            }

            // Return the (possibly mutated) bucket so compute() stores it back in the map
            return bucket;
        });

        return result[0];
    }

    /**
     * Clears all rate limit state for the given client.
     * Used by the admin API to manually unblock a client.
     *
     * Key format is "ruleId|scope|clientId|endpoint".
     * Matching on "|clientId|" avoids false matches on other segments.
     *
     * Example: clientId="10.0.0.1"
     *   Matches  → "r1|IP|10.0.0.1|/api"      ✓  (|10.0.0.1| is present)
     *   No match → "r1|IP|192.10.0.1|/api"     ✗  (different client)
     */
    @Override
    public void reset(RateLimitKey key) {
        String clientSegment = "|" + key.clientId() + "|";
        tokenBuckets.keySet().removeIf(k -> k.contains(clientSegment));
    }

    /**
     * In-memory store is always healthy — there is no external dependency to fail.
     * RedisRateLimitStore (Phase 6) overrides this with a real health check.
     */
    @Override
    public boolean isHealthy() {
        return true;
    }

    /**
     * Builds a unique string key for the map.
     * Pipe (|) separator makes reset() matching unambiguous.
     * Example: "r1|IP|10.0.0.1|/api/users"
     */
    private String buildKey(RateLimitKey key, RateLimitRule rule) {
        return rule.ruleId() + "|" + key.scope() + "|" + key.clientId() + "|" + key.endpoint();
    }
}
