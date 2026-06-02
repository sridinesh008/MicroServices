package com.ratelimiter.model;

public record RateLimitKey(RateLimitScope scope, String clientId, String endpoint) {
}
