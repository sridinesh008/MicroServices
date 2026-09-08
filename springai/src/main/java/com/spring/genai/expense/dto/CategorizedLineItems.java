package com.spring.genai.expense.dto;

import java.util.List;

/**
 * Structured-output target for {@code ExpenseCategorizationService}'s LLM call. {@code expenseDate}
 * is ISO-8601 ({@code yyyy-MM-dd}) if the LLM found a date on the receipt/text, else null (the
 * caller defaults to the submission date).
 */
public record CategorizedLineItems(List<LineItemExtraction> items, String expenseDate) {
}
