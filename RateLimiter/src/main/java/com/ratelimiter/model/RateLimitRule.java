package com.ratelimiter.model;

public record RateLimitRule(
        String ruleId,
        String endpointPattern,
        String userTier,
        AlgorithmType algorithmType,
        int capacity,
        int priority,
        double refillRatePerSecond,
        RateLimitScope scope,
        boolean enabled
) {
}
