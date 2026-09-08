package com.spring.genai.guardrail;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.spring.genai.config.AssistantProperties;

/**
 * Sliding-minute per-principal request cap on conversational LLM calls (/api/chat).
 * Local/in-memory by design — the sibling RateLimiter microservice is the distributed version
 * of this control; this one only needs to survive a single pod with 5 users. Has its own quota
 * bucket, separate from {@link ExpenseRateLimiter}, so one feature can't starve the other.
 */
@Component
public class PerUserRateLimiter {

	private final SlidingMinuteRateLimiter limiter;

	@Autowired
	public PerUserRateLimiter(AssistantProperties properties) {
		this(properties, Clock.systemUTC());
	}

	PerUserRateLimiter(AssistantProperties properties, Clock clock) {
		this.limiter = new SlidingMinuteRateLimiter(properties.getGuardrail().getMaxRequestsPerMinute(), clock);
	}

	public boolean tryAcquire(String principal) {
		return limiter.tryAcquire(principal);
	}

}
