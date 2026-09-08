package com.spring.genai.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.spring.genai.config.AssistantProperties;

class PerUserRateLimiterTest {

	private static class MutableClock extends Clock {
		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advanceSeconds(long seconds) {
			now = now.plusSeconds(seconds);
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	private AssistantProperties propertiesWithLimit(int limit) {
		AssistantProperties properties = new AssistantProperties();
		properties.getGuardrail().setMaxRequestsPerMinute(limit);
		return properties;
	}

	@Test
	void allowsUpToTheConfiguredLimit() {
		MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
		PerUserRateLimiter limiter = new PerUserRateLimiter(propertiesWithLimit(3), clock);

		assertThat(limiter.tryAcquire("alice")).isTrue();
		assertThat(limiter.tryAcquire("alice")).isTrue();
		assertThat(limiter.tryAcquire("alice")).isTrue();
		assertThat(limiter.tryAcquire("alice")).isFalse();
	}

	@Test
	void tracksEachPrincipalIndependently() {
		MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
		PerUserRateLimiter limiter = new PerUserRateLimiter(propertiesWithLimit(1), clock);

		assertThat(limiter.tryAcquire("alice")).isTrue();
		assertThat(limiter.tryAcquire("bob")).isTrue();
		assertThat(limiter.tryAcquire("alice")).isFalse();
	}

	@Test
	void resetsAfterTheWindowElapses() {
		MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
		PerUserRateLimiter limiter = new PerUserRateLimiter(propertiesWithLimit(1), clock);

		assertThat(limiter.tryAcquire("alice")).isTrue();
		assertThat(limiter.tryAcquire("alice")).isFalse();

		clock.advanceSeconds(61);

		assertThat(limiter.tryAcquire("alice")).isTrue();
	}
}
