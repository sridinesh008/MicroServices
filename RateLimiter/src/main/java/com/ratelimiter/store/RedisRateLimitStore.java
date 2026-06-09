package com.ratelimiter.store;

import com.ratelimiter.model.AlgorithmType;
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
 * Supports TOKEN_BUCKET (rl:tb:* keys) and FIXED_WINDOW (rl:fw:* keys).
 *
 * Thread safety: Lua scripts execute atomically on the Redis server.
 * Key schema:
 *   Token bucket : rl:tb:{ruleId}:{scope}:{clientId}:{endpoint}
 *   Fixed window : rl:fw:{ruleId}:{scope}:{clientId}:{endpoint}:{windowId}
 */
public class RedisRateLimitStore implements RateLimitStore {

    private final StringRedisTemplate redisTemplate;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final RedisScript<List<Long>> TOKEN_BUCKET_SCRIPT  = buildScript("scripts/token_bucket.lua");

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final RedisScript<List<Long>> FIXED_WINDOW_SCRIPT  = buildScript("scripts/fixed_window.lua");

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RedisScript<List<Long>> buildScript(String path) {
        DefaultRedisScript script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(List.class);
        return script;
    }

    public RedisRateLimitStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitResult tryConsumeTokens(RateLimitKey key, RateLimitRule rule, int tokens) {
        return switch (rule.algorithmType()) {
            case FIXED_WINDOW -> fixedWindow(key, rule, tokens);
            default           -> tokenBucket(key, rule, tokens);
        };
    }

    /**
     * Executes the Lua token bucket script atomically.
     *
     * TTL formula: ceil(capacity / refillRate) × 2
     * Example: capacity=5, rate=1.0 → 10 seconds
     */
    private RateLimitResult tokenBucket(RateLimitKey key, RateLimitRule rule, int tokens) {
        String storeKey = buildKey("rl:tb", key, rule);
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
        long    secondsToFull = (long) Math.ceil((rule.capacity() - remaining) / rule.refillRatePerSecond());
        long    resetAt    = nowMs / 1000 + (allowed ? secondsToFull : retryAfter);

        return new RateLimitResult(allowed, remaining, rule.capacity(), resetAt, retryAfter);
    }

    /**
     * Executes the Lua fixed window script atomically.
     *
     * Window size = 1000 / refillRatePerSecond milliseconds.
     * Keys are suffixed with windowId and expire after two windows.
     */
    private RateLimitResult fixedWindow(RateLimitKey key, RateLimitRule rule, int cost) {
        String storeKey    = buildKey("rl:fw", key, rule);
        long   nowMs       = System.currentTimeMillis();
        long   windowSizeMs = Math.max(1L, (long)(1000.0 / rule.refillRatePerSecond()));

        List<Long> result = redisTemplate.execute(
                FIXED_WINDOW_SCRIPT,
                List.of(storeKey),
                String.valueOf(rule.capacity()),
                String.valueOf(windowSizeMs),
                String.valueOf(nowMs),
                String.valueOf(cost)
        );

        boolean allowed    = result.get(0) == 1L;
        long    remaining  = result.get(1);
        long    retryAfter = result.get(2);
        long    windowId   = nowMs / windowSizeMs;
        long    windowEnd  = (windowId + 1) * windowSizeMs / 1000;

        return new RateLimitResult(allowed, remaining, rule.capacity(), windowEnd, retryAfter);
    }

    /**
     * Clears all rate limit keys for the given client across all algorithms.
     *
     * Scans rl:* keys (covers rl:tb:* and rl:fw:*) using SCAN to avoid blocking.
     * Rule keys (rl:rule:*, rl:rules:*) have fewer colon-delimited segments so the
     * parts.length >= 5 guard excludes them safely.
     *
     * Key field layout (split on ":", limit=6):
     *   idx: 0   1     2        3      4         5
     *        rl  tb/fw {ruleId} {scope} {clientId} {endpoint[:{windowId}]}
     */
    @Override
    public void reset(RateLimitKey key) {
        String clientId  = key.clientId();
        ScanOptions opts = ScanOptions.scanOptions().match("rl:*").count(100).build();

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            List<byte[]> toDelete = new ArrayList<>();

            try (Cursor<byte[]> cursor = connection.keyCommands().scan(opts)) {
                while (cursor.hasNext()) {
                    byte[]   rawKey = cursor.next();
                    String   k      = new String(rawKey, StandardCharsets.UTF_8);
                    String[] parts  = k.split(":", 6);
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

    /** Key format: {prefix}:{ruleId}:{scope}:{clientId}:{endpoint} */
    private String buildKey(String prefix, RateLimitKey key, RateLimitRule rule) {
        return prefix + ":" + rule.ruleId() + ":" + key.scope() + ":"
                + key.clientId() + ":" + key.endpoint();
    }
}
