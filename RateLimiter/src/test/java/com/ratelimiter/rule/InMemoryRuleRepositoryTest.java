package com.ratelimiter.rule;

import com.ratelimiter.model.AlgorithmType;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RateLimitScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRuleRepositoryTest {

    private final InMemoryRuleRepository repo = new InMemoryRuleRepository();

    private RateLimitRule rule(String id) {
        return new RateLimitRule(id, "/api/**", "default", AlgorithmType.TOKEN_BUCKET, 10, 1, 1.0, RateLimitScope.IP, true);
    }

    @Test
    void saveAndFindById() {
        repo.save(rule("r1"));
        assertThat(repo.findById("r1")).isPresent();
    }

    @Test
    void findByIdUnknownReturnsEmpty() {
        assertThat(repo.findById("unknown")).isEmpty();
    }

    @Test
    void findAllReturnsAllSaved() {
        repo.save(rule("r1"));
        repo.save(rule("r2"));
        assertThat(repo.findAll()).hasSize(2);
    }

    @Test
    void deleteRemovesRule() {
        repo.save(rule("r1"));
        repo.delete("r1");
        assertThat(repo.findById("r1")).isEmpty();
    }

    @Test
    void saveOverwritesExistingId() {
        repo.save(rule("r1"));
        RateLimitRule updated = new RateLimitRule("r1", "/new/**", "premium", AlgorithmType.TOKEN_BUCKET, 100, 5, 10.0, RateLimitScope.IP, true);
        repo.save(updated);
        assertThat(repo.findById("r1").get().endpointPattern()).isEqualTo("/new/**");
    }
}
