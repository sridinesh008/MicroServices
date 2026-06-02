package com.ratelimiter.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitKeyTest {

    @Test
    void equalWhenSameFields() {
        var key1 = new RateLimitKey(RateLimitScope.IP, "192.168.1.1", "/api/users");
        var key2 = new RateLimitKey(RateLimitScope.IP, "192.168.1.1", "/api/users");
        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    void notEqualWhenClientIdDiffers() {
        var key1 = new RateLimitKey(RateLimitScope.IP, "192.168.1.1", "/api/users");
        var key2 = new RateLimitKey(RateLimitScope.IP, "192.168.1.2", "/api/users");
        assertNotEquals(key1, key2);
    }

    @Test
    void hasNoSetterMethods() {
        boolean hasSetters = Arrays.stream(RateLimitKey.class.getMethods())
                .anyMatch(m -> m.getName().startsWith("set"));
        assertFalse(hasSetters, "Record must not expose setters");
    }
}
