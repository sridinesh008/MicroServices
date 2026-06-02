package com.ratelimiter.rule;

import com.ratelimiter.model.RateLimitRule;

import java.util.List;
import java.util.Optional;

public interface RateLimitRuleRepository {
    List<RateLimitRule> findAll();
    Optional<RateLimitRule> findById(String id);
    RateLimitRule save(RateLimitRule rule);
    void delete(String id);
}
