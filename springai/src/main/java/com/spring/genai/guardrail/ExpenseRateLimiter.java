package com.spring.genai.guardrail;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.spring.genai.config.AssistantProperties;

/**
 * Sliding-minute per-principal request cap on the LLM-calling expense endpoints
 * (/api/expenses/text, /api/expenses/image). Separate quota bucket from {@link PerUserRateLimiter}
 * so expense logging and general chat don't compete for the same budget.
 */
@Component
public class ExpenseRateLimiter {

	private final SlidingMinuteRateLimiter limiter;

	@Autowired
	public ExpenseRateLimiter(AssistantProperties properties) {
		this(properties, Clock.systemUTC());
	}

	ExpenseRateLimiter(AssistantProperties properties, Clock clock) {
		this.limiter = new SlidingMinuteRateLimiter(properties.getGuardrail().getMaxExpenseRequestsPerMinute(), clock);
	}

	public boolean tryAcquire(String principal) {
		return limiter.tryAcquire(principal);
	}

}
