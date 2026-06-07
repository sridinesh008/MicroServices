package com.ratelimiter.demo;

import com.ratelimiter.model.AlgorithmType;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RateLimitScope;
import com.ratelimiter.rule.RateLimitRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Demo-only seed data. Remove after Phase 12 (Admin API) ships.
@Component
@Profile("dev")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private final RateLimitRuleRepository repository;

    public DemoDataLoader(RateLimitRuleRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Rule 1: IP-scoped, capacity=3, covers all /api/** — easy to exhaust
        // Token Bucket: starts full (3 tokens). Each request costs 1. Refill: 1 token/sec.
        // Example: requests 1,2,3 → 200. Request 4 → 429. Wait 1 sec → 1 token back.
        repository.save(new RateLimitRule(
            "demo-api-broad",
            "/api/**",
            "default",
            AlgorithmType.TOKEN_BUCKET,
            3,      // capacity: 3 tokens max
            1,      // priority: 1 (lowest — fallback rule)
            1.0,    // refillRatePerSecond: 1 token/sec
            RateLimitScope.IP,
            true
        ));

        // Rule 2: IP-scoped, capacity=1, specific path — higher priority wins over Rule 1
        // Example: /api/v1/demo/resolve/user priority=10 > /api/** priority=1 → this rule applies
        // 1 token max, refill=0.1/sec (1 token per 10 sec) → very strict
        repository.save(new RateLimitRule(
            "demo-user-strict",
            "/api/v1/demo/resolve/user",
            "default",
            AlgorithmType.TOKEN_BUCKET,
            1,      // capacity: 1 token max
            10,     // priority: 10 (beats demo-api-broad for this exact path)
            0.1,    // refillRatePerSecond: 0.1/sec = 1 token per 10 seconds
            RateLimitScope.IP,
            true
        ));

        log.info("[DemoDataLoader] Seeded {} demo rules", repository.findAll().size());
    }
}
