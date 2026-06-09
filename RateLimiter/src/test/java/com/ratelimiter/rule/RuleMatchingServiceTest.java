package com.ratelimiter.rule;

import com.ratelimiter.model.AlgorithmType;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RateLimitScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RuleMatchingServiceTest {

    private InMemoryRuleRepository repository;
    private RuleMatchingService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRuleRepository();
        service = new RuleMatchingService(repository);
    }

    private RateLimitRule rule(String id, String pattern, int priority, boolean enabled) {
        return new RateLimitRule(id, pattern, "default", AlgorithmType.TOKEN_BUCKET, 10, priority, 1.0, RateLimitScope.IP, enabled);
    }

    @Test
    void noRulesReturnsEmpty() {
        var request = new MockHttpServletRequest("GET", "/api/users");
        assertThat(service.findBestMatch(request)).isEmpty();
    }

    @Test
    void returnsSingleMatchingRule() {
        repository.save(rule("r1", "/api/**", 1, true));
        var request = new MockHttpServletRequest("GET", "/api/users");
        assertThat(service.findBestMatch(request))
            .isPresent()
            .get().extracting(RateLimitRule::ruleId).isEqualTo("r1");
    }

    @Test
    void highestPriorityWinsWhenMultipleMatch() {
        repository.save(rule("low",  "/api/**",       1,  true));
        repository.save(rule("high", "/api/users/**", 10, true));
        var request = new MockHttpServletRequest("GET", "/api/users/123");
        assertThat(service.findBestMatch(request))
            .isPresent()
            .get().extracting(RateLimitRule::ruleId).isEqualTo("high");
    }

    @Test
    void disabledRuleNeverReturned() {
        repository.save(rule("r1", "/api/**", 1, false));
        var request = new MockHttpServletRequest("GET", "/api/users");
        assertThat(service.findBestMatch(request)).isEmpty();
    }

    @Test
    void globPatternMatchesNestedPath() {
        repository.save(rule("r1", "/api/**", 1, true));
        var request = new MockHttpServletRequest("GET", "/api/orders/42/items");
        assertThat(service.findBestMatch(request)).isPresent();
    }

    @Test
    void exactPatternDoesNotMatchSubpath() {
        repository.save(rule("r1", "/api/users", 1, true));
        var request = new MockHttpServletRequest("GET", "/api/users/123");
        assertThat(service.findBestMatch(request)).isEmpty();
    }

    // --- userTier tests (item #3) ---

    private RateLimitRule tieredRule(String id, String pattern, int priority, String tier) {
        return new RateLimitRule(id, pattern, tier, AlgorithmType.TOKEN_BUCKET, 10, priority, 1.0, RateLimitScope.IP, true);
    }

    @Test
    void blankTierRuleMatchesAnyRequest() {
        repository.save(tieredRule("r1", "/api/**", 1, ""));
        var request = new MockHttpServletRequest("GET", "/api/users");
        // no X-User-Tier header — blank tier is wildcard, must match
        assertThat(service.findBestMatch(request)).isPresent();
    }

    @Test
    void specificTierRuleMatchesCorrectTierHeader() {
        repository.save(tieredRule("r1", "/api/**", 1, "premium"));
        var request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("X-User-Tier", "premium");
        assertThat(service.findBestMatch(request)).isPresent();
    }

    @Test
    void specificTierRuleNoMatchWhenHeaderAbsent() {
        repository.save(tieredRule("r1", "/api/**", 1, "premium"));
        var request = new MockHttpServletRequest("GET", "/api/users");
        // no X-User-Tier header → rule requires "premium" → no match
        assertThat(service.findBestMatch(request)).isEmpty();
    }

    @Test
    void specificTierRuleNoMatchWhenWrongTier() {
        repository.save(tieredRule("r1", "/api/**", 1, "premium"));
        var request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("X-User-Tier", "free");
        assertThat(service.findBestMatch(request)).isEmpty();
    }

    @Test
    void wildcardTierRuleBeatenByExactTierRule() {
        repository.save(tieredRule("wildcard", "/api/**", 1, ""));
        repository.save(tieredRule("exact",    "/api/**", 10, "premium"));
        var request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("X-User-Tier", "premium");
        // both match; higher priority (exact) wins
        assertThat(service.findBestMatch(request))
            .isPresent()
            .get().extracting(RateLimitRule::ruleId).isEqualTo("exact");
    }
}
