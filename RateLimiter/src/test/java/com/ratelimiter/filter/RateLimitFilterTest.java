package com.ratelimiter.filter;

import com.ratelimiter.model.AlgorithmType;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RateLimitScope;
import com.ratelimiter.rule.InMemoryRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitFilterTest {

    @Autowired MockMvc mockMvc;
    @Autowired InMemoryRuleRepository repository;

    @BeforeEach
    void clearRules() {
        repository.findAll().forEach(r -> repository.delete(r.ruleId()));
    }

    // UUID per call → unique store bucket → test isolation without resetting the store
    private RateLimitRule ipRule(String pattern, int capacity) {
        return new RateLimitRule("test-" + UUID.randomUUID(), pattern, "default",
            AlgorithmType.TOKEN_BUCKET, capacity, 1, 1.0, RateLimitScope.IP, true);
    }

    @Test
    void allowedRequestReturnsOkWithRateLimitHeaders() throws Exception {
        repository.save(ipRule("/api/**", 5));

        mockMvc.perform(get("/api/v1/demo/resolve/ip"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-RateLimit-Limit"))
            .andExpect(header().exists("X-RateLimit-Remaining"))
            .andExpect(header().exists("X-RateLimit-Reset"));
    }

    @Test
    void exhaustedLimitReturns429WithRetryAfterAndJsonBody() throws Exception {
        repository.save(ipRule("/api/**", 2));

        mockMvc.perform(get("/api/v1/demo/resolve/ip")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/demo/resolve/ip")).andExpect(status().isOk());

        // 3rd request → 429
        mockMvc.perform(get("/api/v1/demo/resolve/ip"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.error").value("rate_limit_exceeded"))
            .andExpect(jsonPath("$.retryAfter").isNumber());
    }

    @Test
    void actuatorPathBypassesRateLimit() throws Exception {
        repository.save(ipRule("/**", 1)); // capacity=1 — 2nd request would be 429 without bypass

        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()); // still 200
    }

    @Test
    void noMatchingRulePassesThroughWithNoRateLimitHeaders() throws Exception {
        // no rules in repo → fail-open, no X-RateLimit-* headers written
        mockMvc.perform(get("/api/v1/demo/resolve/ip"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("X-RateLimit-Limit"));
    }

    @Test
    void remainingDecrementsAcrossSequentialRequests() throws Exception {
        repository.save(ipRule("/api/**", 5));

        mockMvc.perform(get("/api/v1/demo/resolve/ip"))
            .andExpect(header().string("X-RateLimit-Remaining", "4"));

        mockMvc.perform(get("/api/v1/demo/resolve/ip"))
            .andExpect(header().string("X-RateLimit-Remaining", "3"));
    }
}
