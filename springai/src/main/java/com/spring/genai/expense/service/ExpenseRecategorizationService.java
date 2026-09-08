package com.spring.genai.expense.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spring.genai.expense.Expense;
import com.spring.genai.expense.ExpenseRepository;
import com.spring.genai.expense.ExpenseStatus;
import com.spring.genai.rules.CategorizationRule;
import com.spring.genai.rules.CategorizationRuleService;
import com.spring.genai.users.AppUser;

/**
 * Manual, current-month-only bulk recategorization ("reapply rules" dashboard button).
 * Always operates on {@link LocalDate#now()} -- there is no year/month parameter, so a client
 * can never point this at another month.
 *
 * <p>Item-level detail and uploaded images are never retained, so this re-derives a category
 * from whatever context survives: the expense's current category/description, plus the
 * originating batch's raw text for TEXT-mode submissions. IMAGE-mode expenses have no original
 * receipt content to re-read.
 */
@Service
public class ExpenseRecategorizationService {

	private final ExpenseRepository expenseRepository;
	private final CategorizationRuleService ruleService;
	private final ExpenseCategorizationService categorizationService;

	public ExpenseRecategorizationService(ExpenseRepository expenseRepository, CategorizationRuleService ruleService,
			ExpenseCategorizationService categorizationService) {
		this.expenseRepository = expenseRepository;
		this.ruleService = ruleService;
		this.categorizationService = categorizationService;
	}

	@Transactional
	public int reapplyRulesToCurrentMonth(AppUser owner) {
		LocalDate now = LocalDate.now();
		List<Expense> expenses = expenseRepository.findByOwnerAndStatusAndExpenseYearAndExpenseMonth(
				owner, ExpenseStatus.CONFIRMED, now.getYear(), now.getMonthValue());
		if (expenses.isEmpty()) {
			return 0;
		}
		List<CategorizationRule> activeRules = ruleService.activeRulesSnapshot(owner);
		if (activeRules.isEmpty()) {
			return 0;
		}

		int updated = 0;
		for (Expense expense : expenses) {
			String suggested = categorizationService.suggestCategory(buildContext(expense), activeRules);
			if (suggested != null && !suggested.isBlank() && !suggested.equalsIgnoreCase(expense.getCategory())) {
				expense.recategorize(suggested);
				updated++;
			}
		}
		return updated;
	}

	private String buildContext(Expense expense) {
		StringBuilder context = new StringBuilder("Current category: ").append(expense.getCategory());
		if (expense.getDescription() != null && !expense.getDescription().isBlank()) {
			context.append(". Description: ").append(expense.getDescription());
		}
		if (expense.getBatch() != null && expense.getBatch().getRawUserText() != null) {
			context.append(". Original message: ").append(expense.getBatch().getRawUserText());
		}
		return context.toString();
	}

}
