package com.ratelimiter.rule;

import com.ratelimiter.model.RateLimitRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRuleRepository implements RateLimitRuleRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRuleRepository.class);

    private final ConcurrentHashMap<String, RateLimitRule> store = new ConcurrentHashMap<>();

    @Override
    public List<RateLimitRule> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public Optional<RateLimitRule> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public RateLimitRule save(RateLimitRule rule) {
        store.put(rule.ruleId(), rule);
        log.info("[RuleRepository] Saved rule id={} pattern={} capacity={} priority={}",
            rule.ruleId(), rule.endpointPattern(), rule.capacity(), rule.priority());
        return rule;
    }

    @Override
    public void delete(String id) {
        RateLimitRule removed = store.remove(id);
        if (removed != null) {
            log.info("[RuleRepository] Deleted rule id={}", id);
        } else {
            log.warn("[RuleRepository] Delete requested for unknown rule id={}", id);
        }
    }
}
