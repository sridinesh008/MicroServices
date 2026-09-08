package com.spring.genai.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Direct manual entry -- no LLM round trip, {@code expenseDate} null means today. */
public record ManualExpenseRequest(
		@NotBlank String description,
		@NotNull @DecimalMin("0.01") BigDecimal amount,
		@NotBlank String category,
		LocalDate expenseDate) {
}
