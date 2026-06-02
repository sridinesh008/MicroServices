package com.ratelimiter.limiter;

import com.ratelimiter.model.AlgorithmType;
import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RateLimitScope;
import com.ratelimiter.store.InMemoryRateLimitStore;
import com.ratelimiter.store.RateLimitStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketRateLimiterTest {

    private TokenBucketRateLimiter limiter;
    private RateLimitRule rule;
    private RateLimitKey key;

    /**
     * Fresh limiter, rule (capacity=5, refill=1 token/sec), and key before each test.
     */
    @BeforeEach
    void setUp() {
        limiter = new TokenBucketRateLimiter(new InMemoryRateLimitStore());
        rule = new RateLimitRule("r1", "/api", "FREE",
                AlgorithmType.TOKEN_BUCKET, 5, 1, 1.0, RateLimitScope.IP, true);
        key = new RateLimitKey(RateLimitScope.IP, "10.0.0.1", "/api");
    }

    /**
     * First request: bucket initializes to capacity=5, consumes 1 → allowed, remaining=4.
     */
    @Test
    void firstRequestIsAllowed() {
        RateLimitResult result = limiter.checkLimit(key, rule);
        assertTrue(result.allowed());
        assertEquals(4L, result.remainingTokens());
        assertEquals(5L, result.limitCapacity());
    }

    /**
     * Each allowed request decrements the remaining token count by 1.
     * capacity=5 → after 3 requests: remaining=2.
     */
    @Test
    void remainingTokensCountsDownCorrectly() {
        limiter.checkLimit(key, rule);  // remaining=4
        limiter.checkLimit(key, rule);  // remaining=3
        RateLimitResult third = limiter.checkLimit(key, rule);  // remaining=2
        assertTrue(third.allowed());
        assertEquals(2L, third.remainingTokens());
    }

    /**
     * After 5 allowed requests (capacity=5), the 6th must be denied.
     */
    @Test
    void exhaustionDeniesNextRequest() {
        for (int i = 0; i < 5; i++) {
            limiter.checkLimit(key, rule);
        }
        RateLimitResult denied = limiter.checkLimit(key, rule);
        assertFalse(denied.allowed());
    }

    /**
     * Denied response must carry retryAfterSeconds > 0 so the client knows when to retry.
     * With refillRate=1 token/sec and 0 tokens left: retryAfter = ceil(1/1.0) = 1 second.
     */
    @Test
    void deniedResultHasPositiveRetryAfterSeconds() {
        for (int i = 0; i < 5; i++) {
            limiter.checkLimit(key, rule);
        }
        RateLimitResult denied = limiter.checkLimit(key, rule);
        assertFalse(denied.allowed());
        assertTrue(denied.retryAfterSeconds() > 0,
                "Denied response must tell client how long to wait");
    }

    /**
     * reset() clears stored state. Next request after reset gets a fresh full bucket.
     */
    @Test
    void resetRestoresBucketAfterExhaustion() {
        for (int i = 0; i < 5; i++) {
            limiter.checkLimit(key, rule);
        }
        assertFalse(limiter.checkLimit(key, rule).allowed());  // confirm exhausted

        limiter.reset(key);

        assertTrue(limiter.checkLimit(key, rule).allowed());   // fresh bucket
    }

    /**
     * isAvailable() delegates to the backing store's isHealthy().
     *
     * Verified with an inline stub that returns false — no mocking framework needed.
     * InMemoryRateLimitStore always returns true, so we need the stub for the false case.
     */
    @Test
    void isAvailableDelegatesToStoreHealth() {
        // InMemoryRateLimitStore is always healthy
        assertTrue(limiter.isAvailable());

        // Stub store that reports unhealthy
        RateLimitStore unhealthy = new RateLimitStore() {
            @Override
            public RateLimitResult tryConsumeTokens(RateLimitKey k, RateLimitRule r, int t) {
                return null;
            }
            @Override
            public void reset(RateLimitKey k) {}
            @Override
            public boolean isHealthy() { return false; }
        };

        TokenBucketRateLimiter unhealthyLimiter = new TokenBucketRateLimiter(unhealthy);
        assertFalse(unhealthyLimiter.isAvailable());
    }

    /**
     * 10 threads all call checkLimit simultaneously. Only 5 (= capacity) should be allowed.
     *
     * CountDownLatch pattern:
     *   ready (10) — each thread signals it is standing by
     *   start (1)  — main thread fires all 10 at once after ready hits 0
     *
     * AtomicInteger counts allowed across threads safely (plain int++ would race).
     */
    @Test
    void concurrentRequestsRespectCapacity() throws InterruptedException {
        int threadCount = 10;
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
                    if (limiter.checkLimit(key, rule).allowed()) {
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
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(capacity, allowed.get());
    }
}
