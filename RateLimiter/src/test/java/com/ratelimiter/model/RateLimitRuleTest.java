package com.ratelimiter.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitRuleTest {

    private RateLimitRule build(boolean enabled) {
        return new RateLimitRule(
                "rule-1", "/api/**", "FREE",
                AlgorithmType.TOKEN_BUCKET,
                10, 1, 1.0,
                RateLimitScope.IP, enabled
        );
    }

    @Test
    void enabledAndDisabledAreDistinct() {
        assertNotEquals(build(true), build(false));
        assertTrue(build(true).enabled());
        assertFalse(build(false).enabled());
    }

    @Test
    void equalWhenSameFields() {
        assertEquals(build(true), build(true));
    }

    @Test
    void allFieldsAccessible() {
        var rule = build(true);
        assertEquals("rule-1", rule.ruleId());
        assertEquals("/api/**", rule.endpointPattern());
        assertEquals("FREE", rule.userTier());
        assertEquals(AlgorithmType.TOKEN_BUCKET, rule.algorithmType());
        assertEquals(10, rule.capacity());
        assertEquals(1, rule.priority());
        assertEquals(1.0, rule.refillRatePerSecond());
        assertEquals(RateLimitScope.IP, rule.scope());
    }
}
