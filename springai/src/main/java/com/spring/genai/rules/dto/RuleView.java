package com.spring.genai.rules.dto;

import java.time.Instant;

import com.spring.genai.rules.CategorizationRule;

public record RuleView(Long id, String instructionText, boolean active, Instant createdAt) {

	public static RuleView from(CategorizationRule rule) {
		return new RuleView(rule.getId(), rule.getInstructionText(), rule.isActive(), rule.getCreatedAt());
	}

}
