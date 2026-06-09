package com.ratelimiter.store;

import com.ratelimiter.model.AlgorithmType;
import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RateLimitScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRateLimitStoreTest {

    private InMemoryRateLimitStore store;
    private RateLimitRule rule;
    private RateLimitKey key;

    /**
     * Runs before every test. Creates a fresh store, a rule (capacity=5, refill=1/sec),
     * and a key representing client IP 10.0.0.1 hitting /api.
     */
    @BeforeEach
    void setUp() {
        store = new InMemoryRateLimitStore();
        rule = new RateLimitRule("r1", "/api", "FREE",
                AlgorithmType.TOKEN_BUCKET, 5, 1, 1.0, RateLimitScope.IP, true);
        key = new RateLimitKey(RateLimitScope.IP, "10.0.0.1", "/api");
    }

    /**
     * In-memory has no external dependency, so isHealthy() is always true.
     * Redis implementation (Phase 6) will actually ping the server here.
     */
    @Test
    void isAlwaysHealthy() {
        assertTrue(store.isHealthy());
    }

    /**
     * The very first request for a key has no stored state.
     * The store must initialize the bucket to full capacity (5 tokens),
     * consume 1, and return remaining=4.
     */
    @Test
    void firstRequestInitializesBucketAndAllows() {
        RateLimitResult result = store.tryConsumeTokens(key, rule, 1);
        assertTrue(result.allowed());
        assertEquals(4L, result.remainingTokens());  // 5 - 1 = 4
        assertEquals(5L, result.limitCapacity());
    }

    /**
     * After consuming all 5 tokens, the 6th request must be denied.
     * retryAfterSeconds > 0 tells the client how long to wait for a refill.
     */
    @Test
    void exhaustingCapacityDeniesNextRequest() {
        for (int i = 0; i < 5; i++) {
            store.tryConsumeTokens(key, rule, 1);  // drain all tokens
        }
        RateLimitResult denied = store.tryConsumeTokens(key, rule, 1);  // 6th → denied
        assertFalse(denied.allowed());
        assertTrue(denied.retryAfterSeconds() > 0);
    }

    /**
     * reset() clears all stored state for a client.
     * After reset, the next request gets a fresh full bucket → allowed.
     *
     * This simulates the admin manually unblocking a client.
     */
    @Test
    void resetClearsBucketSoNextRequestIsAllowed() {
        for (int i = 0; i < 5; i++) {
            store.tryConsumeTokens(key, rule, 1);
        }
        assertFalse(store.tryConsumeTokens(key, rule, 1).allowed());  // confirm exhausted

        store.reset(key);  // admin clears the state

        assertTrue(store.tryConsumeTokens(key, rule, 1).allowed());   // fresh bucket
    }

    /**
     * This is the most important test in Phase 3 — it proves thread safety.
     *
     * Setup:
     *   - capacity = 5  (only 5 requests should be allowed)
     *   - 10 threads all try to consume 1 token at exactly the same moment
     *
     * How CountDownLatch works here:
     *   1. Each thread calls ready.countDown() → decrements the "ready" counter
     *   2. Each thread then calls start.await() → blocks until the latch hits 0
     *   3. Main thread calls ready.await() → waits until all 10 threads are blocked at step 2
     *   4. Main thread calls start.countDown() → releases all 10 threads simultaneously
     *
     * This maximizes contention — all 10 threads hit compute() at the same time.
     * Without atomic compute(), the count could exceed 5 (race condition).
     * With compute(), exactly 5 must be allowed, no more, no less.
     *
     * AtomicInteger is used for the counter because multiple threads increment it —
     * a plain int++ would itself be a race condition.
     */
    // --- Fixed Window tests (item #5) ---

    private RateLimitRule fwRule(int capacity, double refillRate) {
        return new RateLimitRule("fw1", "/api", "FREE",
                AlgorithmType.FIXED_WINDOW, capacity, 1, refillRate, RateLimitScope.IP, true);
    }

    @Test
    void fixedWindow_firstRequestAllowed() {
        RateLimitResult result = store.tryConsumeTokens(key, fwRule(5, 1.0), 1);
        assertTrue(result.allowed());
        assertEquals(4L, result.remainingTokens());
    }

    @Test
    void fixedWindow_exhaustingCapacityDenies() {
        RateLimitRule fw = fwRule(3, 1.0);
        for (int i = 0; i < 3; i++) {
            store.tryConsumeTokens(key, fw, 1);
        }
        RateLimitResult denied = store.tryConsumeTokens(key, fw, 1);
        assertFalse(denied.allowed());
        assertTrue(denied.retryAfterSeconds() >= 1);
    }

    @Test
    void fixedWindow_resetClearsCount() {
        RateLimitRule fw = fwRule(3, 1.0);
        for (int i = 0; i < 3; i++) {
            store.tryConsumeTokens(key, fw, 1);
        }
        assertFalse(store.tryConsumeTokens(key, fw, 1).allowed());
        store.reset(key);
        assertTrue(store.tryConsumeTokens(key, fw, 1).allowed());
    }

    @Test
    void concurrentRequestsRespectCapacity() throws InterruptedException {
        int threadCount = 10;
        int capacity = 5;
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threadCount);  // counts down from 10 to 0
        CountDownLatch start = new CountDownLatch(1);            // counts down from 1 to 0
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();   // "I'm ready"
                try {
                    start.await();   // wait for the starting gun
                    if (store.tryConsumeTokens(key, rule, 1).allowed()) {
                        allowed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();    // wait until all 10 threads are standing by
        start.countDown(); // fire — all 10 threads released at once
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        // Exactly capacity threads should have been allowed — not 4, not 6
        assertEquals(capacity, allowed.get());
    }
}
