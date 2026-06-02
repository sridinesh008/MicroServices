package com.ratelimiter.model;

public record RateLimitResult(
        boolean allowed,
        long remainingTokens,
        long limitCapacity,
        long resetAtEpochSeconds,
        long retryAfterSeconds
) {
}
