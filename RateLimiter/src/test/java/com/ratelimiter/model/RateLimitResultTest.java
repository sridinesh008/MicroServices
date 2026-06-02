package com.ratelimiter.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitResultTest {

    @Test
    void allowedResultCarriesRemainingTokens() {
        var result = new RateLimitResult(true, 4L, 5L, 1700000060L, 0L);
        assertTrue(result.allowed());
        assertEquals(4L, result.remainingTokens());
        assertEquals(0L, result.retryAfterSeconds());
    }

    @Test
    void deniedResultCarriesRetryAfter() {
        var result = new RateLimitResult(false, 0L, 5L, 1700000060L, 3L);
        assertFalse(result.allowed());
        assertEquals(0L, result.remainingTokens());
        assertEquals(3L, result.retryAfterSeconds());
    }

    @Test
    void equalWhenSameFields() {
        var r1 = new RateLimitResult(true, 4L, 5L, 1700000060L, 0L);
        var r2 = new RateLimitResult(true, 4L, 5L, 1700000060L, 0L);
        assertEquals(r1, r2);
    }
}
