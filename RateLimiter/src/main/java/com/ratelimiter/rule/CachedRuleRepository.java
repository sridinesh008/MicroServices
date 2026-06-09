package com.ratelimiter.rule;

import com.ratelimiter.model.RateLimitRule;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Write-through local cache over RedisRuleRepository.
 *
 * At 4000 TPS, findAll() is called on every request. This wrapper ensures
 * zero Redis calls on the hot path — rules are served from a ConcurrentHashMap.
 *
 * Write path: save/delete update both Redis and the local map immediately.
 *
 * Cross-instance sync: when another instance writes a rule, Redis publishes
 * "RELOAD" on rl:rules:events. All instances receive it and call reload(),
 * which re-fetches all rules from Redis without clearing the map first
 * (avoids a gap where findAll() returns nothing during the swap).
 */
public class CachedRuleRepository implements RateLimitRuleRepository {

    private static final Logger log = LoggerFactory.getLogger(CachedRuleRepository.class);

    private final RedisRuleRepository delegate;
    private final RedisConnectionFactory connectionFactory;
    private final ConcurrentHashMap<String, RateLimitRule> cache = new ConcurrentHashMap<>();

    private RedisMessageListenerContainer listenerContainer;

    public CachedRuleRepository(RedisRuleRepository delegate, RedisConnectionFactory connectionFactory) {
        this.delegate          = delegate;
        this.connectionFactory = connectionFactory;
    }

    @PostConstruct
    void init() {
        reload();
        tryStartListenerContainer();
    }

    @PreDestroy
    void destroy() {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }

    /** Reloads all rules from Redis into the local cache.
     *  Updates in-place (no clear) to avoid a gap where findAll returns nothing. */
    public void reload() {
        try {
            List<RateLimitRule> rules = delegate.findAll();
            Set<String> incoming = new HashSet<>();
            rules.forEach(r -> {
                cache.put(r.ruleId(), r);
                incoming.add(r.ruleId());
            });
            cache.keySet().retainAll(incoming);
            log.info("[CachedRuleRepository] Reloaded {} rules from Redis", cache.size());
        } catch (Exception e) {
            log.warn("[CachedRuleRepository] Could not reload rules from Redis: {}", e.getMessage());
        }
    }

    @Override
    public List<RateLimitRule> findAll() {
        return List.copyOf(cache.values());
    }

    @Override
    public Optional<RateLimitRule> findById(String id) {
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public RateLimitRule save(RateLimitRule rule) {
        RateLimitRule saved = delegate.save(rule);
        cache.put(saved.ruleId(), saved);
        return saved;
    }

    @Override
    public void delete(String id) {
        delegate.delete(id);
        cache.remove(id);
    }

    private void tryStartListenerContainer() {
        try {
            listenerContainer = new RedisMessageListenerContainer();
            listenerContainer.setConnectionFactory(connectionFactory);
            listenerContainer.addMessageListener(
                (message, pattern) -> reload(),
                new ChannelTopic(RedisRuleRepository.EVENTS_CHANNEL)
            );
            listenerContainer.afterPropertiesSet();
            listenerContainer.start();
            log.info("[CachedRuleRepository] Pub/sub listener started on channel {}",
                RedisRuleRepository.EVENTS_CHANNEL);
        } catch (Exception e) {
            log.warn("[CachedRuleRepository] Could not start pub/sub listener: {}", e.getMessage());
        }
    }
}
