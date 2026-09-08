package com.spring.genai.expense.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.spring.genai.expense.Expense;
import com.spring.genai.expense.ExpenseRepository;
import com.spring.genai.expense.ExpenseStatus;
import com.spring.genai.rules.CategorizationRule;
import com.spring.genai.rules.CategorizationRuleService;
import com.spring.genai.users.AppUser;

/**
 * Manual, current-month-only bulk recategorization ("reapply rules" dashboard button / chat
 * tool). Always operates on {@link LocalDate#now()} -- there is no year/month parameter, so a
 * client can never point this at another month.
 *
 * <p>Item-level detail and uploaded images are never retained, so this re-derives a category
 * from whatever context survives: the expense's current category/description, plus the
 * originating batch's raw text for TEXT-mode submissions. IMAGE-mode expenses have no original
 * receipt content to re-read.
 *
 * <p>The per-expense LLM categorization calls dominate wall time and are independent of one
 * another, so they run off the DB transaction and bounded-parallel instead of one at a time
 * inside a single held connection -- previously N confirmed expenses meant N sequential LLM
 * round trips inside one open {@code @Transactional} method, pinning a DB connection for
 * minutes on a month with many expenses.
 */
@Service
public class ExpenseRecategorizationService {

	private static final int MAX_CONCURRENT_LLM_CALLS = 8;

	private final ExpenseRepository expenseRepository;
	private final CategorizationRuleService ruleService;
	private final ExpenseCategorizationService categorizationService;
	private final TransactionTemplate transactionTemplate;

	public ExpenseRecategorizationService(ExpenseRepository expenseRepository, CategorizationRuleService ruleService,
			ExpenseCategorizationService categorizationService, PlatformTransactionManager transactionManager) {
		this.expenseRepository = expenseRepository;
		this.ruleService = ruleService;
		this.categorizationService = categorizationService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	private record Candidate(Long expenseId, String currentCategory, String context) {
	}

	private record Suggestion(Long expenseId, String category) {
	}

	public int reapplyRulesToCurrentMonth(AppUser owner) {
		LocalDate now = LocalDate.now();

		List<Candidate> candidates = transactionTemplate.execute(status -> {
			List<Expense> expenses = expenseRepository.findByOwnerAndStatusAndExpenseYearAndExpenseMonth(
					owner, ExpenseStatus.CONFIRMED, now.getYear(), now.getMonthValue());
			return expenses.stream()
					.map(e -> new Candidate(e.getId(), e.getCategory(), buildContext(e)))
					.toList();
		});
		if (candidates.isEmpty()) {
			return 0;
		}

		List<CategorizationRule> activeRules = ruleService.activeRulesSnapshot(owner);
		if (activeRules.isEmpty()) {
			return 0;
		}

		List<Suggestion> suggestions = suggestCategoriesInParallel(candidates, activeRules);
		Map<Long, String> currentCategoryById = candidates.stream()
				.collect(Collectors.toMap(Candidate::expenseId, Candidate::currentCategory));

		Integer updated = transactionTemplate.execute(status -> {
			int count = 0;
			for (Suggestion suggestion : suggestions) {
				if (suggestion.category() == null || suggestion.category().isBlank()) {
					continue;
				}
				String currentCategory = currentCategoryById.get(suggestion.expenseId());
				if (suggestion.category().equalsIgnoreCase(currentCategory)) {
					continue;
				}
				Expense expense = expenseRepository.findById(suggestion.expenseId()).orElse(null);
				if (expense == null) {
					continue;
				}
				expense.recategorize(suggestion.category());
				count++;
			}
			return count;
		});
		return updated;
	}

	private List<Suggestion> suggestCategoriesInParallel(List<Candidate> candidates,
			List<CategorizationRule> activeRules) {
		int concurrency = Math.min(candidates.size(), MAX_CONCURRENT_LLM_CALLS);
		try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
			Function<Candidate, CompletableFuture<Suggestion>> suggest = candidate -> CompletableFuture.supplyAsync(
					() -> new Suggestion(candidate.expenseId(),
							categorizationService.suggestCategory(candidate.context(), activeRules)),
					executor);
			List<CompletableFuture<Suggestion>> futures = candidates.stream().map(suggest).toList();
			return futures.stream().map(CompletableFuture::join).toList();
		}
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
