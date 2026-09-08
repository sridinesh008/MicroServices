package com.spring.genai.expense.service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.spring.genai.expense.dto.CategorizedLineItems;
import com.spring.genai.expense.dto.LineItemExtraction;

/**
 * Pure rollup logic: line items -> category totals. E.g. apple 1kg-100 + orange 0.5kg-50 both
 * categorized "Fruits" -> {"Fruits": 150}. This is the boundary past which item-level detail
 * never travels.
 */
@Component
public class ExpenseAggregationService {

	public Map<String, BigDecimal> aggregateByCategory(CategorizedLineItems extraction) {
		Map<String, BigDecimal> totals = new LinkedHashMap<>();
		for (LineItemExtraction item : extraction.items()) {
			totals.merge(item.category(), item.amount(), BigDecimal::add);
		}
		return totals;
	}

	public LocalDate resolveExpenseDate(CategorizedLineItems extraction, LocalDate fallback) {
		if (extraction.expenseDate() == null || extraction.expenseDate().isBlank()) {
			return fallback;
		}
		try {
			return LocalDate.parse(extraction.expenseDate());
		} catch (DateTimeException ex) {
			return fallback;
		}
	}

}
