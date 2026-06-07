package com.ratelimiter.store;

import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Circuit breaker wrapper: Redis (primary) with InMemory (fallback).
 *
 * Uses Resilience4j CircuitBreaker — production-grade sliding window, half-open probes,
 * actuator health integration, event publishing.
 *
 * State machine (managed by Resilience4j):
 *   CLOSED    → failure rate ≥ threshold over sliding window → OPEN
 *   OPEN      → wait-duration-in-open-state ms → HALF_OPEN (automatic or manual)
 *   HALF_OPEN → N permitted probe calls → all succeed → CLOSED; any fail → OPEN
 *
 * Example (prod config: window=10, min-calls=5, threshold=50%, wait=5s, probes=3):
 *   10 calls, 6 fail → 60% ≥ 50% → OPEN
 *   After 5s → HALF_OPEN → 3 probe calls → all succeed → CLOSED
 */
public class FallbackRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(FallbackRateLimitStore.class);

    private final RateLimitStore primary;
    private final RateLimitStore fallback;
    private final CircuitBreaker circuitBreaker;

    public FallbackRateLimitStore(RateLimitStore primary,
                                  RateLimitStore fallback,
                                  CircuitBreaker circuitBreaker) {
        this.primary        = primary;
        this.fallback       = fallback;
        this.circuitBreaker = circuitBreaker;

        // Log every state transition: CLOSED→OPEN, OPEN→HALF_OPEN, HALF_OPEN→CLOSED etc.
        circuitBreaker.getEventPublisher().onStateTransition(e ->
            log.warn("[FallbackStore] Circuit {} → {}",
                e.getStateTransition().getFromState(),
                e.getStateTransition().getToState()));
    }

    /**
     * Attempts primary (Redis). Falls back to InMemory on any failure or when circuit is OPEN.
     *
     * Resilience4j handles failure counting and state transitions transparently.
     * CallNotPermittedException is thrown immediately when OPEN — primary never called.
     */
    @Override
    public RateLimitResult tryConsumeTokens(RateLimitKey key, RateLimitRule rule, int tokens) {
        try {
            return circuitBreaker.executeSupplier(() -> primary.tryConsumeTokens(key, rule, tokens));
        } catch (CallNotPermittedException e) {
            // Circuit is OPEN — primary bypassed, use fallback silently
            log.debug("[FallbackStore] Circuit {} — using InMemory fallback", circuitBreaker.getState());
            return fallback.tryConsumeTokens(key, rule, tokens);
        } catch (Exception e) {
            // Primary threw an exception — Resilience4j already recorded the failure
            log.warn("[FallbackStore] Primary failed ({}), using InMemory fallback: {}",
                circuitBreaker.getState(), e.getMessage());
            return fallback.tryConsumeTokens(key, rule, tokens);
        }
    }

    @Override
    public void reset(RateLimitKey key) {
        try { primary.reset(key); } catch (Exception ignored) {}
        fallback.reset(key);
    }

    /**
     * Healthy only when circuit is CLOSED and primary confirms health.
     * OPEN or HALF_OPEN → unhealthy (even if Redis is back — wait for probe to confirm).
     */
    @Override
    public boolean isHealthy() {
        return circuitBreaker.getState() == CircuitBreaker.State.CLOSED && primary.isHealthy();
    }
}
