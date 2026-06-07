package com.ratelimiter.store;

import com.ratelimiter.model.AlgorithmType;
import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RateLimitScope;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FallbackRateLimitStoreTest {

    @Mock RateLimitStore primary;

    private InMemoryRateLimitStore fallback;
    private CircuitBreaker circuitBreaker;
    private FallbackRateLimitStore store;

    private RateLimitRule rule;
    private RateLimitKey  key;

    /*
     * Tight config for unit tests:
     *   window=3, min=3, threshold=100% → exactly 3 failures → OPEN
     *   wait=1h  → circuit never auto-closes mid-test
     *   probes=1 → one probe call in HALF_OPEN determines CLOSED vs OPEN
     *   automaticTransition=false → tests call transitionToHalfOpenState() manually
     */
    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(3)
            .minimumNumberOfCalls(3)
            .failureRateThreshold(100f)
            .waitDurationInOpenState(Duration.ofHours(1))
            .permittedNumberOfCallsInHalfOpenState(1)
            .automaticTransitionFromOpenToHalfOpenEnabled(false)
            .build();

        circuitBreaker = CircuitBreaker.of("test", config);
        fallback       = new InMemoryRateLimitStore();
        store          = new FallbackRateLimitStore(primary, fallback, circuitBreaker);

        rule = new RateLimitRule("r1", "/api/**", "default",
            AlgorithmType.TOKEN_BUCKET, 5, 1, 1.0, RateLimitScope.IP, true);
        key  = new RateLimitKey(RateLimitScope.IP, "127.0.0.1", "/api/test");
    }

    // ── CLOSED state ──────────────────────────────────────────────────────────

    @Test
    void whenCircuitClosed_delegatesToPrimary() {
        RateLimitResult expected = allowed(4, 5);
        when(primary.tryConsumeTokens(key, rule, 1)).thenReturn(expected);

        RateLimitResult result = store.tryConsumeTokens(key, rule, 1);

        assertThat(result).isEqualTo(expected);
        verify(primary).tryConsumeTokens(key, rule, 1);
    }

    // ── OPEN state ────────────────────────────────────────────────────────────

    @Test
    void afterThreeFailures_circuitOpens() {
        // window=3, min=3, threshold=100% → 3 failures → 100% >= 100% → OPEN
        when(primary.tryConsumeTokens(key, rule, 1)).thenThrow(new RuntimeException("Redis down"));
        for (int i = 0; i < 3; i++) store.tryConsumeTokens(key, rule, 1);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void whenCircuitOpen_primaryIsNeverCalled() {
        when(primary.tryConsumeTokens(key, rule, 1)).thenThrow(new RuntimeException());
        for (int i = 0; i < 3; i++) store.tryConsumeTokens(key, rule, 1);
        reset(primary); // clear call history so verifyNoInteractions is clean

        store.tryConsumeTokens(key, rule, 1);
        store.tryConsumeTokens(key, rule, 1);

        verifyNoInteractions(primary);
    }

    @Test
    void whenCircuitOpen_fallbackServesRequests() {
        when(primary.tryConsumeTokens(key, rule, 1)).thenThrow(new RuntimeException());
        for (int i = 0; i < 3; i++) store.tryConsumeTokens(key, rule, 1);

        // fallback (InMemory) has capacity=5 → allows
        RateLimitResult result = store.tryConsumeTokens(key, rule, 1);
        assertThat(result.allowed()).isTrue();
    }

    // ── HALF_OPEN / Recovery ──────────────────────────────────────────────────

    @Test
    void whenHalfOpen_successfulProbe_circuitCloses() {
        // Chained stub: throw 3× to open circuit, then return for the probe call.
        // Avoids re-stubbing (which STRICT_STUBS rejects mid-test).
        when(primary.tryConsumeTokens(key, rule, 1))
            .thenThrow(new RuntimeException("Redis down"))
            .thenThrow(new RuntimeException("Redis down"))
            .thenThrow(new RuntimeException("Redis down"))
            .thenReturn(allowed(4, 5));

        for (int i = 0; i < 3; i++) store.tryConsumeTokens(key, rule, 1);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Simulate wait expired — manually transition to HALF_OPEN
        circuitBreaker.transitionToHalfOpenState();

        // Probe call succeeds → Resilience4j records success → CLOSED
        store.tryConsumeTokens(key, rule, 1);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void whenHalfOpen_failedProbe_circuitReopens() {
        // Open the circuit
        when(primary.tryConsumeTokens(key, rule, 1)).thenThrow(new RuntimeException());
        for (int i = 0; i < 3; i++) store.tryConsumeTokens(key, rule, 1);

        // Transition to HALF_OPEN — probe fails → back to OPEN
        circuitBreaker.transitionToHalfOpenState();
        store.tryConsumeTokens(key, rule, 1); // primary still throws

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    // ── isHealthy ─────────────────────────────────────────────────────────────

    @Test
    void isHealthy_trueWhenClosedAndPrimaryHealthy() {
        when(primary.isHealthy()).thenReturn(true);
        assertThat(store.isHealthy()).isTrue();
    }

    @Test
    void isHealthy_falseWhenCircuitOpen() {
        when(primary.tryConsumeTokens(key, rule, 1)).thenThrow(new RuntimeException());
        for (int i = 0; i < 3; i++) store.tryConsumeTokens(key, rule, 1);

        assertThat(store.isHealthy()).isFalse();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private static RateLimitResult allowed(long remaining, long capacity) {
        long reset = System.currentTimeMillis() / 1000 + 60;
        return new RateLimitResult(true, remaining, capacity, reset, 0);
    }
}
