package com.ratelimiter.config;

import com.ratelimiter.store.InMemoryRateLimitStore;
import com.ratelimiter.store.RateLimitStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class DevProfileConfigTest {

    @Autowired ApplicationContext ctx;
    @Autowired RateLimitStore store;

    @Test
    void devProfileWiresInMemoryStore() {
        // dev profile sets rate-limiter.store-type: in-memory
        assertThat(store).isInstanceOf(InMemoryRateLimitStore.class);
    }

    @Test
    void devProfileDemoDataLoaderIsPresent() {
        // DemoDataLoader is @Profile("dev") — must exist in dev context
        assertThat(ctx.containsBean("demoDataLoader")).isTrue();
    }
}
