package com.spring.genai.expense.dto;

import jakarta.validation.constraints.NotBlank;

public record TextExpenseRequest(@NotBlank String message) {
}
