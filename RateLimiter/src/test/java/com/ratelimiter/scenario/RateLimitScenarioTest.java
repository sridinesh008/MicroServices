package com.ratelimiter.scenario;

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
class RateLimitScenarioTest {

    @Autowired MockMvc mockMvc;
    @Autowired InMemoryRuleRepository repository;

    @BeforeEach
    void clearRules() {
        repository.findAll().forEach(r -> repository.delete(r.ruleId()));
    }

    // UUID per test → unique ruleId → unique store bucket → no cross-test bleed
    private RateLimitRule ipRule(String pattern, int capacity) {
        return new RateLimitRule(
            "sc-" + UUID.randomUUID(), pattern, "default",
            AlgorithmType.TOKEN_BUCKET, capacity, 1, 1.0, RateLimitScope.IP, true
        );
    }

    /**
     * Scenario: capacity=3 → requests 1,2,3 pass → request 4 blocked.
     * Token Bucket: starts with 3 tokens. Each request costs 1.
     * After 3 consumed: 0 tokens → next request denied.
     */
    @Test
    void fourthRequestReturns429WhenCapacityIsThree() throws Exception {
        repository.save(ipRule("/api/**", 3));

        mockMvc.perform(get("/api/v1/demo/resolve/ip")).andExpect(status().isOk());       // token 3→2
        mockMvc.perform(get("/api/v1/demo/resolve/ip")).andExpect(status().isOk());       // token 2→1
        mockMvc.perform(get("/api/v1/demo/resolve/ip")).andExpect(status().isOk());       // token 1→0
        mockMvc.perform(get("/api/v1/demo/resolve/ip")).andExpect(status().isTooManyRequests()); // 0→DENY
    }

    /**
     * Scenario: X-RateLimit-Remaining decrements with each request.
     * capacity=3 → remaining after req1=2, req2=1, req3=0.
     */
    @Test
    void remainingTokensDecrementWithEachRequest() throws Exception {
        repository.save(ipRule("/api/**", 3));

        mockMvc.perform(get("/api/v1/demo/resolve/ip"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-RateLimit-Remaining", "2")); // 3-1=2

        mockMvc.perform(get("/api/v1/demo/resolve/ip"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-RateLimit-Remaining", "1")); // 2-1=1

        mockMvc.perform(get("/api/v1/demo/resolve/ip"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-RateLimit-Remaining", "0")); // 1-1=0
    }

    /**
     * Scenario: 429 response has correct shape — Retry-After, X-RateLimit-* headers, JSON body.
     */
    @Test
    void deniedResponseContainsAllRequiredFieldsAndHeaders() throws Exception {
        repository.save(ipRule("/api/**", 1));

        mockMvc.perform(get("/api/v1/demo/resolve/ip")).andExpect(status().isOk()); // 1 token consumed

        mockMvc.perform(get("/api/v1/demo/resolve/ip"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(header().string("X-RateLimit-Limit", "1"))
            .andExpect(header().string("X-RateLimit-Remaining", "0"))
            .andExpect(header().exists("X-RateLimit-Reset"))
            .andExpect(jsonPath("$.error").value("rate_limit_exceeded"))
            .andExpect(jsonPath("$.retryAfter").isNumber());
    }

    /**
     * Scenario: /actuator/** is bypassed — never rate limited, no X-RateLimit headers.
     * capacity=1 means 2nd request would 429 without bypass.
     */
    @Test
    void actuatorEndpointBypassedEvenWithTightRule() throws Exception {
        repository.save(ipRule("/**", 1)); // capacity=1 — 2nd would 429 without bypass

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-RateLimit-Limit")); // no rate limit headers on bypass
        }
    }

    /**
     * Scenario: two different IPs share the same rule but have independent token buckets.
     * Store key = ruleId|scope|clientId|endpoint → different clientId (IP) = different bucket.
     * IP-A and IP-B each get capacity=1 independently.
     */
    @Test
    void differentIpAddressesHaveIndependentTokenBuckets() throws Exception {
        repository.save(ipRule("/api/**", 1));

        // IP-A first request → allowed (bucket: 1→0)
        mockMvc.perform(get("/api/v1/demo/resolve/ip").header("X-Forwarded-For", "10.0.0.1"))
            .andExpect(status().isOk());

        // IP-B first request → allowed (separate bucket: 1→0)
        mockMvc.perform(get("/api/v1/demo/resolve/ip").header("X-Forwarded-For", "10.0.0.2"))
            .andExpect(status().isOk());

        // IP-A second request → 429 (bucket empty)
        mockMvc.perform(get("/api/v1/demo/resolve/ip").header("X-Forwarded-For", "10.0.0.1"))
            .andExpect(status().isTooManyRequests());

        // IP-B second request → 429 (bucket empty)
        mockMvc.perform(get("/api/v1/demo/resolve/ip").header("X-Forwarded-For", "10.0.0.2"))
            .andExpect(status().isTooManyRequests());
    }

    /**
     * Scenario: no rule in repository → fail-open, request passes with no X-RateLimit-* headers.
     */
    @Test
    void noRuleMatchPassesThroughWithNoRateLimitHeaders() throws Exception {
        // no rules saved — repository is empty from @BeforeEach clear

        mockMvc.perform(get("/api/v1/demo/resolve/ip"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("X-RateLimit-Limit"))
            .andExpect(header().doesNotExist("X-RateLimit-Remaining"))
            .andExpect(header().doesNotExist("X-RateLimit-Reset"));
    }
}
