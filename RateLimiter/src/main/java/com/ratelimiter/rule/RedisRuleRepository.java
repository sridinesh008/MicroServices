package com.ratelimiter.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.model.RateLimitRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Redis-backed rule repository.
 *
 * Key schema:
 *   rl:rule:{ruleId}   → String (JSON-serialized RateLimitRule)
 *   rl:rules:index     → Set<String> of all ruleIds
 *   rl:rules:events    → Pub/Sub channel — publishes "RELOAD" on every write/delete
 *
 * Used as the delegate inside CachedRuleRepository, never injected directly.
 */
public class RedisRuleRepository implements RateLimitRuleRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisRuleRepository.class);

    static final String INDEX_KEY    = "rl:rules:index";
    static final String KEY_PREFIX   = "rl:rule:";
    static final String EVENTS_CHANNEL = "rl:rules:events";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisRuleRepository(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis  = redis;
        this.mapper = mapper;
    }

    @Override
    public List<RateLimitRule> findAll() {
        Set<String> ids = redis.opsForSet().members(INDEX_KEY);
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream()
            .map(this::fetchById)
            .filter(r -> r != null)
            .toList();
    }

    @Override
    public Optional<RateLimitRule> findById(String id) {
        return Optional.ofNullable(fetchById(id));
    }

    @Override
    public RateLimitRule save(RateLimitRule rule) {
        try {
            redis.opsForValue().set(KEY_PREFIX + rule.ruleId(), mapper.writeValueAsString(rule));
            redis.opsForSet().add(INDEX_KEY, rule.ruleId());
            redis.convertAndSend(EVENTS_CHANNEL, "RELOAD");
        } catch (Exception e) {
            throw new RuntimeException("Failed to save rule to Redis: " + rule.ruleId(), e);
        }
        log.info("[RedisRuleRepository] Saved rule id={}", rule.ruleId());
        return rule;
    }

    @Override
    public void delete(String id) {
        redis.delete(KEY_PREFIX + id);
        redis.opsForSet().remove(INDEX_KEY, id);
        redis.convertAndSend(EVENTS_CHANNEL, "RELOAD");
        log.info("[RedisRuleRepository] Deleted rule id={}", id);
    }

    private RateLimitRule fetchById(String id) {
        String json = redis.opsForValue().get(KEY_PREFIX + id);
        if (json == null) return null;
        try {
            return mapper.readValue(json, RateLimitRule.class);
        } catch (Exception e) {
            log.warn("[RedisRuleRepository] Could not deserialize rule id={}: {}", id, e.getMessage());
            return null;
        }
    }
}
