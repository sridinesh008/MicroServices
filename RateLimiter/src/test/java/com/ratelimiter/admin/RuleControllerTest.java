package com.ratelimiter.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.model.AlgorithmType;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RateLimitScope;
import com.ratelimiter.rule.RateLimitRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RuleControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired RateLimitRuleRepository repository;
    @Autowired ObjectMapper objectMapper;

    private static final String BASE = "/api/v1/admin/rules";

    @BeforeEach
    void clearRules() {
        repository.findAll().forEach(r -> repository.delete(r.ruleId()));
    }

    private RateLimitRule sampleRule(String id) {
        return new RateLimitRule(id, "/api/**", "default",
            AlgorithmType.TOKEN_BUCKET, 10, 1, 1.0, RateLimitScope.IP, true);
    }

    @Test
    void createRule_returns201WithSavedRule() throws Exception {
        RateLimitRule rule = sampleRule("rule-create-1");

        mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rule)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ruleId").value("rule-create-1"))
            .andExpect(jsonPath("$.capacity").value(10));
    }

    @Test
    void listRules_returnsAllRules() throws Exception {
        repository.save(sampleRule("rule-list-1"));
        repository.save(sampleRule("rule-list-2"));

        mockMvc.perform(get(BASE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getRule_found_returns200() throws Exception {
        repository.save(sampleRule("rule-get-1"));

        mockMvc.perform(get(BASE + "/rule-get-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ruleId").value("rule-get-1"));
    }

    @Test
    void getRule_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE + "/nonexistent"))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateRule_found_returns200WithUpdatedData() throws Exception {
        repository.save(sampleRule("rule-update-1"));

        // update capacity from 10 to 99
        RateLimitRule updated = new RateLimitRule("rule-update-1", "/api/**", "premium",
            AlgorithmType.TOKEN_BUCKET, 99, 2, 2.0, RateLimitScope.IP, true);

        mockMvc.perform(put(BASE + "/rule-update-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updated)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capacity").value(99))
            .andExpect(jsonPath("$.userTier").value("premium"));
    }

    @Test
    void updateRule_notFound_returns404() throws Exception {
        RateLimitRule rule = sampleRule("ghost-rule");

        mockMvc.perform(put(BASE + "/ghost-rule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rule)))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteRule_found_returns204() throws Exception {
        repository.save(sampleRule("rule-delete-1"));

        mockMvc.perform(delete(BASE + "/rule-delete-1"))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteRule_notFound_returns404() throws Exception {
        mockMvc.perform(delete(BASE + "/nonexistent"))
            .andExpect(status().isNotFound());
    }

    // --- Validation tests (item #6) ---

    @Test
    void createRule_blankRuleId_returns400() throws Exception {
        RateLimitRule bad = new RateLimitRule("", "/api/**", "default",
            AlgorithmType.TOKEN_BUCKET, 10, 1, 1.0, RateLimitScope.IP, true);
        mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bad)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createRule_negativeCapacity_returns400() throws Exception {
        RateLimitRule bad = new RateLimitRule("rule-neg-cap", "/api/**", "default",
            AlgorithmType.TOKEN_BUCKET, -1, 1, 1.0, RateLimitScope.IP, true);
        mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bad)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createRule_zeroRefillRate_returns400() throws Exception {
        RateLimitRule bad = new RateLimitRule("rule-zero-refill", "/api/**", "default",
            AlgorithmType.TOKEN_BUCKET, 10, 1, 0.0, RateLimitScope.IP, true);
        mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bad)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createRule_blankEndpointPattern_returns400() throws Exception {
        RateLimitRule bad = new RateLimitRule("rule-blank-ep", "", "default",
            AlgorithmType.TOKEN_BUCKET, 10, 1, 1.0, RateLimitScope.IP, true);
        mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bad)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createRule_nullScope_returns400() throws Exception {
        String json = "{\"ruleId\":\"r1\",\"endpointPattern\":\"/api/**\",\"algorithmType\":\"TOKEN_BUCKET\"," +
                      "\"capacity\":10,\"priority\":1,\"refillRatePerSecond\":1.0,\"enabled\":true}";
        mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());
    }
}
