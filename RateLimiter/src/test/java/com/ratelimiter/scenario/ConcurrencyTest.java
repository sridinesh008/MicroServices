package com.ratelimiter.scenario;

import com.ratelimiter.model.AlgorithmType;
import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RateLimitScope;
import com.ratelimiter.store.InMemoryRateLimitStore;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies InMemoryRateLimitStore is thread-safe under concurrent load.
 *
 * Token bucket: capacity=50, 10 threads × 10 requests = 100 total.
 * Expected: exactly 50 allowed, 50 denied — no double-counting.
 *
 * refillRate=0.001 token/sec → ~0.00005 tokens refilled during test (~50ms).
 * Too small to matter, so "allowed" stays at exactly 50.
 *
 * Thread-safety mechanism: ConcurrentHashMap.compute() locks one bucket key
 * atomically — the entire read-refill-deduct-write happens under that lock.
 */
class ConcurrencyTest {

    @Test
    void concurrentRequests_exactlyCapacityAllowed_noDoubleCount() throws InterruptedException {
        int threads          = 10;
        int requestsPerThread = 10;
        int capacity         = 50; // total tokens = total threads × requestsPerThread / 2

        InMemoryRateLimitStore store = new InMemoryRateLimitStore();

        // refillRate=0.001 → negligible refill during the ~50ms this test runs
        RateLimitRule rule = new RateLimitRule("r-concurrency", "/api/**", "default",
            AlgorithmType.TOKEN_BUCKET, capacity, 1, 0.001, RateLimitScope.IP, true);
        RateLimitKey key = new RateLimitKey(RateLimitScope.IP, "10.0.0.1", "/api/test");

        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger denied  = new AtomicInteger(0);

        // ready: all threads signal they reached the start line
        // start: main thread fires the gun — all threads begin simultaneously
        // done:  all threads signal completion
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);

        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(); // block until all threads are ready — maximises contention
                    for (int j = 0; j < requestsPerThread; j++) {
                        RateLimitResult result = store.tryConsumeTokens(key, rule, 1);
                        if (result.allowed()) allowed.incrementAndGet();
                        else                  denied.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();       // wait until all threads are at start line
        start.countDown();   // fire — all 10 threads begin concurrently
        boolean finished = done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("Threads did not finish within timeout").isTrue();

        int total = threads * requestsPerThread; // 100
        assertThat(allowed.get() + denied.get())
            .as("No requests lost or double-fired")
            .isEqualTo(total);

        // exactly capacity tokens consumed — ConcurrentHashMap.compute() must prevent double-count
        assertThat(allowed.get())
            .as("Allowed count must equal bucket capacity — race condition if > 50")
            .isEqualTo(capacity);

        assertThat(denied.get())
            .as("Remaining requests denied after bucket empty")
            .isEqualTo(total - capacity);
    }
}
