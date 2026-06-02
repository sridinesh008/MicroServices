package com.ratelimiter.rule;

import com.ratelimiter.model.RateLimitRule;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.Comparator;
import java.util.Optional;

@Service
public class RuleMatchingService {

    private static final Logger log = LoggerFactory.getLogger(RuleMatchingService.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final RateLimitRuleRepository repository;

    public RuleMatchingService(RateLimitRuleRepository repository) {
        this.repository = repository;
    }

    /**
     * Finds best matching rule for incoming request.
     * Matching: endpointPattern vs URI (Ant glob), enabled=true only.
     * Tiebreak: highest priority wins.
     * Example: "/api/**" priority=1 vs "/api/users/**" priority=10 → "/api/users/**" wins for /api/users/123
     */
    public Optional<RateLimitRule> findBestMatch(HttpServletRequest request) {
        String uri = request.getRequestURI();
        log.debug("[RuleMatchingService] Finding best match for URI={}", uri);

        Optional<RateLimitRule> best = repository.findAll().stream()
            .filter(RateLimitRule::enabled)
            .filter(rule -> PATH_MATCHER.match(rule.endpointPattern(), uri))
            .max(Comparator.comparingInt(RateLimitRule::priority));

        if (best.isPresent()) {
            log.info("[RuleMatchingService] Matched rule id={} pattern={} priority={}",
                best.get().ruleId(), best.get().endpointPattern(), best.get().priority());
        } else {
            log.debug("[RuleMatchingService] No matching rule for URI={}", uri);
        }
        return best;
    }
}
