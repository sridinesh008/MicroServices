package com.spring.genai.rules.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRuleRequest(@NotBlank String instructionText) {
}
