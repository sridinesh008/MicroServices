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

    // No real controller at this path — rate-limiter-allowed requests return 404,
    // rate-limited requests return 429. Tests verify filter behavior, not handler output.
    private static final String TARGET = "/api/v1/test/probe";

    @BeforeEach
    void clearRules() {
        repository.findAll().forEach(r -> repository.delete(r.ruleId()));
    }

    private RateLimitRule ipRule(String pattern, int capacity) {
        return new RateLimitRule(
            "sc-" + UUID.randomUUID(), pattern, "default",
            AlgorithmType.TOKEN_BUCKET, capacity, 1, 1.0, RateLimitScope.IP, true
        );
    }

    @Test
    void fourthRequestReturns429WhenCapacityIsThree() throws Exception {
        repository.save(ipRule("/api/**", 3));

        mockMvc.perform(get(TARGET)).andExpect(status().isNotFound());           // token 3→2
        mockMvc.perform(get(TARGET)).andExpect(status().isNotFound());           // token 2→1
        mockMvc.perform(get(TARGET)).andExpect(status().isNotFound());           // token 1→0
        mockMvc.perform(get(TARGET)).andExpect(status().isTooManyRequests());    // 0→DENY
    }

    @Test
    void remainingTokensDecrementWithEachRequest() throws Exception {
        repository.save(ipRule("/api/**", 3));

        mockMvc.perform(get(TARGET))
            .andExpect(status().isNotFound())
            .andExpect(header().string("X-RateLimit-Remaining", "2"));

        mockMvc.perform(get(TARGET))
            .andExpect(status().isNotFound())
            .andExpect(header().string("X-RateLimit-Remaining", "1"));

        mockMvc.perform(get(TARGET))
            .andExpect(status().isNotFound())
            .andExpect(header().string("X-RateLimit-Remaining", "0"));
    }

    @Test
    void deniedResponseContainsAllRequiredFieldsAndHeaders() throws Exception {
        repository.save(ipRule("/api/**", 1));

        mockMvc.perform(get(TARGET)).andExpect(status().isNotFound()); // 1 token consumed

        mockMvc.perform(get(TARGET))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(header().string("X-RateLimit-Limit", "1"))
            .andExpect(header().string("X-RateLimit-Remaining", "0"))
            .andExpect(header().exists("X-RateLimit-Reset"))
            .andExpect(jsonPath("$.error").value("rate_limit_exceeded"))
            .andExpect(jsonPath("$.retryAfter").isNumber());
    }

    @Test
    void actuatorEndpointBypassedEvenWithTightRule() throws Exception {
        repository.save(ipRule("/**", 1));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-RateLimit-Limit"));
        }
    }

    @Test
    void differentIpAddressesHaveIndependentTokenBuckets() throws Exception {
        repository.save(ipRule("/api/**", 1));

        mockMvc.perform(get(TARGET).header("X-Forwarded-For", "10.0.0.1"))
            .andExpect(status().isNotFound());

        mockMvc.perform(get(TARGET).header("X-Forwarded-For", "10.0.0.2"))
            .andExpect(status().isNotFound());

        mockMvc.perform(get(TARGET).header("X-Forwarded-For", "10.0.0.1"))
            .andExpect(status().isTooManyRequests());

        mockMvc.perform(get(TARGET).header("X-Forwarded-For", "10.0.0.2"))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void noRuleMatchPassesThroughWithNoRateLimitHeaders() throws Exception {
        mockMvc.perform(get(TARGET))
            .andExpect(status().isNotFound())
            .andExpect(header().doesNotExist("X-RateLimit-Limit"))
            .andExpect(header().doesNotExist("X-RateLimit-Remaining"))
            .andExpect(header().doesNotExist("X-RateLimit-Reset"));
    }
}
