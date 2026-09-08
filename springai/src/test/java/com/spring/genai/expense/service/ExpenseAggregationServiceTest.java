package com.spring.genai.expense.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.spring.genai.expense.dto.CategorizedLineItems;
import com.spring.genai.expense.dto.LineItemExtraction;

class ExpenseAggregationServiceTest {

	private final ExpenseAggregationService service = new ExpenseAggregationService();

	@Test
	void rollsUpLineItemsToCategoryTotals() {
		CategorizedLineItems extraction = new CategorizedLineItems(List.of(
				new LineItemExtraction("apple", new BigDecimal("100"), "Fruits"),
				new LineItemExtraction("orange", new BigDecimal("50"), "Fruits"),
				new LineItemExtraction("milk", new BigDecimal("30"), "Milk Products")), null);

		var totals = service.aggregateByCategory(extraction);

		assertThat(totals).containsEntry("Fruits", new BigDecimal("150"));
		assertThat(totals).containsEntry("Milk Products", new BigDecimal("30"));
		assertThat(totals).hasSize(2);
	}

	@Test
	void resolvesExpenseDateFromExtractionWhenPresent() {
		CategorizedLineItems extraction = new CategorizedLineItems(List.of(), "2026-01-15");

		LocalDate resolved = service.resolveExpenseDate(extraction, LocalDate.of(2026, 9, 7));

		assertThat(resolved).isEqualTo(LocalDate.of(2026, 1, 15));
	}

	@Test
	void fallsBackToDefaultDateWhenExtractionHasNone() {
		CategorizedLineItems extraction = new CategorizedLineItems(List.of(), null);

		LocalDate resolved = service.resolveExpenseDate(extraction, LocalDate.of(2026, 9, 7));

		assertThat(resolved).isEqualTo(LocalDate.of(2026, 9, 7));
	}

	@Test
	void fallsBackToDefaultDateWhenExtractionDateIsUnparseable() {
		CategorizedLineItems extraction = new CategorizedLineItems(List.of(), "not-a-date");

		LocalDate resolved = service.resolveExpenseDate(extraction, LocalDate.of(2026, 9, 7));

		assertThat(resolved).isEqualTo(LocalDate.of(2026, 9, 7));
	}

}
