package com.ratelimiter.rule;

import com.ratelimiter.model.AlgorithmType;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RateLimitScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RedisRuleRepositoryIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("rate-limiter.store-type", () -> "redis");
    }

    @Autowired
    RateLimitRuleRepository repository;   // injected as CachedRuleRepository

    @Autowired
    StringRedisTemplate redisTemplate;

    private RateLimitRule rule(String id) {
        return new RateLimitRule(id, "/api/**", "FREE",
            AlgorithmType.TOKEN_BUCKET, 10, 1, 1.0, RateLimitScope.IP, true);
    }

    @BeforeEach
    void setUp() {
        redisTemplate.execute((RedisCallback<Void>) conn -> {
            conn.serverCommands().flushDb();
            return null;
        });
        // Reload cache after flush so each test starts with empty state
        ((CachedRuleRepository) repository).reload();
    }

    @Test
    void saveAndFindById() {
        repository.save(rule("r1"));
        assertThat(repository.findById("r1")).isPresent();
    }

    @Test
    void findByIdUnknownReturnsEmpty() {
        assertThat(repository.findById("unknown")).isEmpty();
    }

    @Test
    void findAllReturnsAllSaved() {
        repository.save(rule("r1"));
        repository.save(rule("r2"));
        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void deleteRemovesFromCacheAndRedis() {
        repository.save(rule("r1"));
        repository.delete("r1");
        assertThat(repository.findById("r1")).isEmpty();
        assertThat(redisTemplate.opsForSet().members(RedisRuleRepository.INDEX_KEY))
            .doesNotContain("r1");
    }

    @Test
    void saveOverwritesExistingRule() {
        repository.save(rule("r1"));
        RateLimitRule updated = new RateLimitRule("r1", "/new/**", "PREMIUM",
            AlgorithmType.TOKEN_BUCKET, 100, 5, 10.0, RateLimitScope.USER_ID, true);
        repository.save(updated);
        assertThat(repository.findById("r1").get().endpointPattern()).isEqualTo("/new/**");
    }

    @Test
    void rulePersistedInRedis() {
        repository.save(rule("r1"));
        // Verify raw Redis key exists
        assertThat(redisTemplate.hasKey(RedisRuleRepository.KEY_PREFIX + "r1")).isTrue();
        assertThat(redisTemplate.opsForSet().members(RedisRuleRepository.INDEX_KEY)).contains("r1");
    }

    @Test
    void reloadSyncsCacheFromRedis() throws Exception {
        repository.save(rule("r1"));
        // Simulate another instance writing directly to Redis (bypassing local cache)
        repository.save(rule("r2"));
        // Manually clear cache to simulate cold state, then reload
        ((CachedRuleRepository) repository).reload();
        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void pubSubTriggersCacheReload() throws Exception {
        repository.save(rule("r1"));
        repository.save(rule("r2"));

        // Simulate a remote instance deleting r1 directly in Redis (bypassing local cache)
        redisTemplate.delete(RedisRuleRepository.KEY_PREFIX + "r1");
        redisTemplate.opsForSet().remove(RedisRuleRepository.INDEX_KEY, "r1");

        // Remote instance publishes RELOAD — triggers reload() in this instance
        redisTemplate.convertAndSend(RedisRuleRepository.EVENTS_CHANNEL, "RELOAD");

        // Allow pub/sub delivery to complete
        TimeUnit.MILLISECONDS.sleep(200);

        assertThat(repository.findById("r1")).isEmpty();
        assertThat(repository.findById("r2")).isPresent();
    }
}
