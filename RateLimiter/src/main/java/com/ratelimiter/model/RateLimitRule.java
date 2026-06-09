package com.ratelimiter.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record RateLimitRule(
        @NotBlank String ruleId,
        @NotBlank String endpointPattern,
        String userTier,
        @NotNull AlgorithmType algorithmType,
        @Positive int capacity,
        @PositiveOrZero int priority,
        @Positive double refillRatePerSecond,
        @NotNull RateLimitScope scope,
        boolean enabled
) {
}
