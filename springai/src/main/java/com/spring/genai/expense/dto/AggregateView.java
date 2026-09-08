package com.spring.genai.expense.dto;

import java.math.BigDecimal;
import java.util.List;

public record AggregateView(List<CategoryTotal> totalsByCategory, List<MonthTotal> totalsByMonth) {

	public record CategoryTotal(String category, BigDecimal total) {
	}

	public record MonthTotal(int year, int month, BigDecimal total) {
	}

}
