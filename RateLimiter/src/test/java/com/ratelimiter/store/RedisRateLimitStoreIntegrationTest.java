package com.ratelimiter.store;

import com.ratelimiter.model.AlgorithmType;
import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RateLimitScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimitStoreIntegrationTest {

    /**
     * Testcontainers starts a real Redis 7.2 container before any test runs.
     * Port 6379 inside the container is mapped to a random host port.
     * This prevents port conflicts when multiple test suites run in parallel.
     */
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    /**
     * Tells Spring Boot to use the container's dynamic host/port instead of
     * the default localhost:6379. Also switches store-type to redis so
     * RateLimiterConfig creates RedisRateLimitStore (not InMemoryRateLimitStore).
     *
     * @DynamicPropertySource runs before the Spring context starts, so the
     * context sees these values as if they were in application.yml.
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("rate-limiter.store-type", () -> "redis");
    }

    @Autowired
    RateLimitStore store;

    @Autowired
    StringRedisTemplate redisTemplate;

    private RateLimitRule rule;
    private RateLimitKey key;

    /**
     * Flush DB before each test so state from one test never leaks into the next.
     * (All tests share the same container — faster than one container per test.)
     */
    @BeforeEach
    void setUp() {
        rule = new RateLimitRule("r1", "/api", "FREE",
                AlgorithmType.TOKEN_BUCKET, 5, 1, 1.0, RateLimitScope.IP, true);
        key = new RateLimitKey(RateLimitScope.IP, "10.0.0.1", "/api");
        redisTemplate.execute((RedisCallback<Void>) conn -> {
            conn.serverCommands().flushDb();
            return null;
        });
    }

    /**
     * PING → PONG. If Redis is up, isHealthy() must return true.
     */
    @Test
    void isHealthyWhenRedisRunning() {
        assertTrue(store.isHealthy());
    }

    /**
     * First request: Lua initializes bucket to capacity=5, consumes 1 → remaining=4.
     * Identical behaviour to InMemoryRateLimitStore (same formula, different runtime).
     */
    @Test
    void firstRequestIsAllowed() {
        RateLimitResult result = store.tryConsumeTokens(key, rule, 1);
        assertTrue(result.allowed());
        assertEquals(4L, result.remainingTokens());
        assertEquals(5L, result.limitCapacity());
    }

    /**
     * After draining all 5 tokens, the 6th must be denied.
     */
    @Test
    void exhaustionDeniesRequest() {
        for (int i = 0; i < 5; i++) store.tryConsumeTokens(key, rule, 1);
        RateLimitResult denied = store.tryConsumeTokens(key, rule, 1);
        assertFalse(denied.allowed());
    }

    /**
     * Denied result must carry retryAfterSeconds > 0.
     * With rate=1 token/sec and 0 tokens left: retryAfter = ceil(1/1.0) = 1 sec.
     */
    @Test
    void deniedResultHasPositiveRetryAfterSeconds() {
        for (int i = 0; i < 5; i++) store.tryConsumeTokens(key, rule, 1);
        RateLimitResult denied = store.tryConsumeTokens(key, rule, 1);
        assertFalse(denied.allowed());
        assertTrue(denied.retryAfterSeconds() > 0);
    }

    /**
     * reset() must remove the Redis key so the next request gets a fresh full bucket.
     */
    @Test
    void resetClearsStateSoNextRequestIsAllowed() {
        for (int i = 0; i < 5; i++) store.tryConsumeTokens(key, rule, 1);
        assertFalse(store.tryConsumeTokens(key, rule, 1).allowed());  // confirm exhausted

        store.reset(key);

        assertTrue(store.tryConsumeTokens(key, rule, 1).allowed());   // fresh bucket
    }

    /**
     * Redis key must have a TTL set after the first request.
     * TTL = ceil(capacity / refillRate) × 2 = ceil(5/1.0) × 2 = 10 seconds.
     *
     * Without TTL, stale keys from inactive clients would live forever.
     */
    @Test
    void redisKeyHasTtlAfterFirstRequest() {
        store.tryConsumeTokens(key, rule, 1);
        String redisKey = "rl:tb:r1:IP:10.0.0.1:/api";
        Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0, "Redis key must have a positive TTL to prevent stale key leaks");
    }

    /**
     * This is the core test for Redis Lua atomicity.
     *
     * 20 threads all fire simultaneously against a capacity=5 bucket.
     * The Lua script runs atomically on the Redis server — no race condition possible.
     * Exactly 5 must be allowed, regardless of how many threads compete.
     *
     * Compare to InMemoryRateLimitStore which uses ConcurrentHashMap.compute().
     * Redis Lua replaces compute() — both are atomic read-modify-write operations.
     *
     * We use 20 threads (vs 10 for in-memory) to stress the Lua script harder.
     */
    @Test
    void concurrentRequestsRespectCapacityViaLuaAtomicity() throws InterruptedException {
        int threadCount = 20;
        int capacity = 5;
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (store.tryConsumeTokens(key, rule, 1).allowed()) {
                        allowed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(capacity, allowed.get(),
                "Lua script must be atomic — exactly capacity threads should be allowed");
    }
}
