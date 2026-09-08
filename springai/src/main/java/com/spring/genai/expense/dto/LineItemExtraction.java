package com.spring.genai.expense.dto;

import java.math.BigDecimal;

/**
 * One line item as read by the LLM (e.g. "apple 1kg - 100rs" -> name=apple, amount=100,
 * category=Fruits). Transient — never persisted; {@code ExpenseAggregationService} rolls
 * these up into category totals before anything touches the database.
 */
public record LineItemExtraction(String name, BigDecimal amount, String category) {
}
