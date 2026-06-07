package com.ratelimiter.demo;

import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RateLimitScope;
import com.ratelimiter.rule.InMemoryRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataLoaderTest {

    private InMemoryRuleRepository repository;
    private DemoDataLoader loader;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRuleRepository();
        loader = new DemoDataLoader(repository);
    }

    @Test
    void seedsTwoRulesOnStartup() throws Exception {
        loader.run(null);
        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void broadRuleHasCorrectConfig() throws Exception {
        loader.run(null);
        RateLimitRule rule = repository.findById("demo-api-broad").orElseThrow();

        assertThat(rule.endpointPattern()).isEqualTo("/api/**");
        assertThat(rule.capacity()).isEqualTo(3);
        assertThat(rule.priority()).isEqualTo(1);
        assertThat(rule.refillRatePerSecond()).isEqualTo(1.0);
        assertThat(rule.scope()).isEqualTo(RateLimitScope.IP);
        assertThat(rule.enabled()).isTrue();
    }

    @Test
    void strictRuleHasCorrectConfig() throws Exception {
        loader.run(null);
        RateLimitRule rule = repository.findById("demo-user-strict").orElseThrow();

        assertThat(rule.endpointPattern()).isEqualTo("/api/v1/demo/resolve/user");
        assertThat(rule.capacity()).isEqualTo(1);
        assertThat(rule.priority()).isEqualTo(10);
        assertThat(rule.refillRatePerSecond()).isEqualTo(0.1);
        assertThat(rule.scope()).isEqualTo(RateLimitScope.IP);
        assertThat(rule.enabled()).isTrue();
    }

    @Test
    void strictRuleHasPriorityHigherThanBroadRule() throws Exception {
        loader.run(null);
        RateLimitRule broad  = repository.findById("demo-api-broad").orElseThrow();
        RateLimitRule strict = repository.findById("demo-user-strict").orElseThrow();

        // strict priority=10 beats broad priority=1 → RuleMatchingService picks strict for /api/v1/demo/resolve/user
        assertThat(strict.priority()).isGreaterThan(broad.priority());
    }

    @Test
    void runningTwiceOverwritesNotDuplicates() throws Exception {
        loader.run(null);
        loader.run(null); // same ruleId → store.put() overwrites, not appends

        assertThat(repository.findAll()).hasSize(2);
    }
}
