package com.spring.genai.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Every field optional -- only non-null ones are applied. Used both for edit-before-confirm and manual recategorization. */
public record EditExpenseRequest(String category, BigDecimal amount, String description, LocalDate expenseDate) {
}
