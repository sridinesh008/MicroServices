package com.spring.genai.expense.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spring.genai.expense.Expense;
import com.spring.genai.expense.ExpenseBatch;
import com.spring.genai.expense.ExpenseBatchRepository;
import com.spring.genai.expense.ExpenseRepository;
import com.spring.genai.expense.ExpenseStatus;
import com.spring.genai.expense.SourceMode;
import com.spring.genai.expense.dto.ExpenseDraftView;
import com.spring.genai.expense.dto.ExpenseView;
import com.spring.genai.expense.service.ExpenseCategorizationService.CategorizationResult;
import com.spring.genai.rules.CategorizationRule;
import com.spring.genai.rules.CategorizationRuleService;
import com.spring.genai.rules.CategorizationRuleService.RuleExtraction;
import com.spring.genai.users.AppUser;

/**
 * Orchestrates one submission (text message, or image upload + optional caption) end to end:
 * rule extraction, LLM categorization with primary/fallback, category-level aggregation, and
 * PENDING_CONFIRMATION persistence. Nothing here is ever created as CONFIRMED -- that only
 * happens via the explicit confirm step.
 */
@Service
public class ExpenseIngestionService {

	private final ExpenseBatchRepository batchRepository;
	private final ExpenseRepository expenseRepository;
	private final CategorizationRuleService ruleService;
	private final ExpenseCategorizationService categorizationService;
	private final ExpenseAggregationService aggregationService;

	public ExpenseIngestionService(ExpenseBatchRepository batchRepository, ExpenseRepository expenseRepository,
			CategorizationRuleService ruleService, ExpenseCategorizationService categorizationService,
			ExpenseAggregationService aggregationService) {
		this.batchRepository = batchRepository;
		this.expenseRepository = expenseRepository;
		this.ruleService = ruleService;
		this.categorizationService = categorizationService;
		this.aggregationService = aggregationService;
	}

	@Transactional
	public ExpenseDraftView ingest(AppUser owner, SourceMode sourceMode, String rawText, List<Media> media,
			int imageCount) {
		RuleExtraction ruleExtraction = ruleService.extract(rawText);
		// Snapshot BEFORE persisting the new rule -- this same message's own expense must not
		// see the rule it just defined.
		List<CategorizationRule> rulesSnapshot = ruleService.activeRulesSnapshot(owner);

		ExpenseBatch batch = batchRepository.save(new ExpenseBatch(owner, sourceMode, rawText, imageCount));

		if (ruleExtraction.hasRule()) {
			ruleService.persist(owner, ruleExtraction.ruleInstruction(), batch);
		}

		String textForCategorization = ruleExtraction.textForCategorization();
		boolean nothingToCategorize = (textForCategorization == null || textForCategorization.isBlank())
				&& media.isEmpty();
		if (nothingToCategorize) {
			// A standalone #newCategorizationRule message with no expense content and no images.
			batch.setStatus(ExpenseStatus.CONFIRMED);
			batchRepository.save(batch);
			return new ExpenseDraftView(batch.getId(), List.of());
		}

		CategorizationResult result = categorizationService.categorize(textForCategorization, media, rulesSnapshot);
		batch.setLlmProvider(result.provider());
		batchRepository.save(batch);

		Map<String, BigDecimal> totals = aggregationService.aggregateByCategory(result.extraction());
		LocalDate expenseDate = aggregationService.resolveExpenseDate(result.extraction(), LocalDate.now());

		List<ExpenseView> views = totals.entrySet().stream()
				.map(e -> expenseRepository.save(
						new Expense(owner, batch, e.getKey(), e.getValue(), null, sourceMode, expenseDate)))
				.map(ExpenseView::from)
				.toList();

		return new ExpenseDraftView(batch.getId(), views);
	}

}
