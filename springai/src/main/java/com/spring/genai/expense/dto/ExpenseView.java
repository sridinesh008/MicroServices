package com.spring.genai.expense.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.spring.genai.expense.Expense;
import com.spring.genai.expense.ExpenseStatus;
import com.spring.genai.expense.SourceMode;

public record ExpenseView(
		Long id,
		Long batchId,
		String category,
		String originalCategory,
		BigDecimal amount,
		String description,
		SourceMode sourceMode,
		ExpenseStatus status,
		LocalDate expenseDate,
		Instant createdAt,
		Instant updatedAt,
		Instant recategorizedAt) {

	public static ExpenseView from(Expense expense) {
		return new ExpenseView(
				expense.getId(),
				expense.getBatch() != null ? expense.getBatch().getId() : null,
				expense.getCategory(),
				expense.getOriginalCategory(),
				expense.getAmount(),
				expense.getDescription(),
				expense.getSourceMode(),
				expense.getStatus(),
				expense.getExpenseDate(),
				expense.getCreatedAt(),
				expense.getUpdatedAt(),
				expense.getRecategorizedAt());
	}

}
