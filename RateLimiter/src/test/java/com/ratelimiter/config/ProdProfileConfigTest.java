package com.ratelimiter.config;

import com.ratelimiter.store.FallbackRateLimitStore;
import com.ratelimiter.store.RateLimitStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("prod")
class ProdProfileConfigTest {

    // StringRedisTemplate is needed by RedisRateLimitStore.
    // @MockBean replaces the real autoconfigured bean so no live Redis required.
    @MockBean StringRedisTemplate stringRedisTemplate;

    @Autowired ApplicationContext ctx;
    @Autowired RateLimitStore store;

    @Test
    void prodProfileWiresRedisStore() {
        // prod profile sets rate-limiter.store-type: redis → wrapped in FallbackRateLimitStore
        assertThat(store).isInstanceOf(FallbackRateLimitStore.class);
    }

    @Test
    void prodProfileDemoDataLoaderIsAbsent() {
        // DemoDataLoader is @Profile("dev") — must NOT exist in prod context
        assertThat(ctx.containsBean("demoDataLoader")).isFalse();
    }
}
