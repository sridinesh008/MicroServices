package com.spring.genai.expense.dto;

import java.util.List;

public record ExpenseDraftView(Long batchId, List<ExpenseView> items) {
}
