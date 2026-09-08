package com.spring.genai.guardrail;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-minute request cap per key. Plain, non-Spring so it can back multiple independent
 * per-purpose limiters (e.g. {@link PerUserRateLimiter}, {@link ExpenseRateLimiter}) without
 * sharing a quota bucket.
 */
class SlidingMinuteRateLimiter {

	private final int maxPerMinute;
	private final Clock clock;
	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	SlidingMinuteRateLimiter(int maxPerMinute, Clock clock) {
		this.maxPerMinute = maxPerMinute;
		this.clock = clock;
	}

	boolean tryAcquire(String key) {
		Instant now = clock.instant();
		Window window = windows.compute(key, (k, existing) -> {
			if (existing == null || existing.isExpired(now)) {
				return new Window(now, 1);
			}
			return existing.increment();
		});
		return window.count() <= maxPerMinute;
	}

	private record Window(Instant start, int count) {
		boolean isExpired(Instant now) {
			return now.isAfter(start.plusSeconds(60));
		}

		Window increment() {
			return new Window(start, count + 1);
		}
	}
}
