package com.ratelimiter.store;

import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Distributed rate limit store backed by Redis.
 *
 * Thread safety: Redis Lua scripts execute atomically on the server.
 * No two scripts touch the same key simultaneously — Redis is single-threaded
 * per key. This replaces ConcurrentHashMap.compute() from InMemoryRateLimitStore.
 *
 * Key schema: rl:tb:{ruleId}:{scope}:{clientId}:{endpoint}
 * Value type: Redis Hash with fields "tokens" and "last_refill_ms"
 */
public class RedisRateLimitStore implements RateLimitStore {

    private final StringRedisTemplate redisTemplate;

    /**
     * Lua script loaded once at startup and cached by Redis (SHA1 hash).
     * Subsequent calls use EVALSHA instead of EVAL — saves network bandwidth.
     *
     * Result type List<Long>: Redis integer array → Java Long elements.
     * [0] = allowed (0 or 1)
     * [1] = floor(remaining tokens)
     * [2] = retry_after_seconds
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final RedisScript<List<Long>> TOKEN_BUCKET_SCRIPT = buildScript();

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RedisScript<List<Long>> buildScript() {
        DefaultRedisScript script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        script.setResultType(List.class);
        return script;
    }

    public RedisRateLimitStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Executes the Lua token bucket script atomically on Redis.
     *
     * TTL formula: ceil(capacity / refillRate) × 2
     * Example: capacity=5, rate=1.0 → ceil(5) × 2 = 10 seconds
     * Gives enough time for a full refill plus a buffer before the key expires.
     */
    @Override
    public RateLimitResult tryConsumeTokens(RateLimitKey key, RateLimitRule rule, int tokens) {
        String storeKey = buildKey(key, rule);
        long   nowMs    = System.currentTimeMillis();
        long   ttl      = (long) Math.ceil(rule.capacity() / rule.refillRatePerSecond()) * 2;

        List<Long> result = redisTemplate.execute(
                TOKEN_BUCKET_SCRIPT,
                List.of(storeKey),
                String.valueOf(rule.capacity()),
                String.valueOf(rule.refillRatePerSecond()),
                String.valueOf(tokens),
                String.valueOf(nowMs),
                String.valueOf(ttl)
        );

        boolean allowed    = result.get(0) == 1L;
        long    remaining  = result.get(1);
        long    retryAfter = result.get(2);

        // resetAt = seconds until bucket is full (allowed) or until retry is safe (denied).
        // Example (allowed): capacity=5, remaining=3, rate=1.0 → ceil((5-3)/1) = 2 sec from now
        // Example (denied):  retryAfter=1 → resetAt = now + 1
        long secondsToFull = (long) Math.ceil((rule.capacity() - remaining) / rule.refillRatePerSecond());
        long resetAt       = nowMs / 1000 + (allowed ? secondsToFull : retryAfter);

        return new RateLimitResult(allowed, remaining, rule.capacity(), resetAt, retryAfter);
    }

    /**
     * Clears all token bucket keys for the given client across all rules.
     *
     * Uses SCAN (not KEYS) to avoid blocking the Redis server on large datasets.
     * SCAN iterates in batches of ~100 keys per call.
     *
     * Key format: rl:tb:{ruleId}:{scope}:{clientId}:{endpoint}
     * Split by ":" with limit=6 → parts[4] is always the clientId.
     * Example: "rl:tb:r1:IP:10.0.0.1:/api" → parts = [rl, tb, r1, IP, 10.0.0.1, /api]
     *                                                  idx: 0   1   2   3   4          5
     */
    @Override
    public void reset(RateLimitKey key) {
        String clientId = key.clientId();
        ScanOptions opts = ScanOptions.scanOptions().match("rl:tb:*").count(100).build();

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            List<byte[]> toDelete = new ArrayList<>();

            try (Cursor<byte[]> cursor = connection.keyCommands().scan(opts)) {
                while (cursor.hasNext()) {
                    byte[] rawKey = cursor.next();
                    String k = new String(rawKey, StandardCharsets.UTF_8);
                    String[] parts = k.split(":", 6);
                    if (parts.length >= 5 && parts[4].equals(clientId)) {
                        toDelete.add(rawKey);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Redis SCAN failed during reset", e);
            }

            if (!toDelete.isEmpty()) {
                connection.keyCommands().del(toDelete.toArray(byte[][]::new));
            }
            return null;
        });
    }

    /**
     * Sends a PING to Redis. Returns true only if the response is "PONG".
     * Used by FallbackRateLimitStore (Phase 11) to detect Redis recovery.
     */
    @Override
    public boolean isHealthy() {
        try {
            String pong = redisTemplate.execute(
                    (RedisCallback<String>) conn -> conn.ping());
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            return false;
        }
    }

    /** Redis key format: rl:tb:{ruleId}:{scope}:{clientId}:{endpoint} */
    private String buildKey(RateLimitKey key, RateLimitRule rule) {
        return "rl:tb:" + rule.ruleId() + ":" + key.scope() + ":"
                + key.clientId() + ":" + key.endpoint();
    }
}
