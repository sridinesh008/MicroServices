package com.spring.genai.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.spring.genai.expense.ExpenseStatus;
import com.spring.genai.expense.SourceMode;

/** Every field optional/nullable -- {@code ExpenseQueryService} only applies the ones present. */
public record ExpenseFilterRequest(
		LocalDate dateFrom,
		LocalDate dateTo,
		Integer year,
		Integer month,
		List<String> categories,
		BigDecimal amountMin,
		BigDecimal amountMax,
		SourceMode sourceMode,
		ExpenseStatus status,
		String search) {
}
