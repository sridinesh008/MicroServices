package com.spring.genai.expense.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
 *
 * <p>The LLM categorization call runs outside any DB transaction. {@link #prepare} and
 * {@link #persist} each hold a connection only for their own quick reads/writes; the
 * multi-second, two-provider-fallback categorization call in between them holds no DB
 * connection at all, instead of pinning one for the whole call as a single wrapping
 * {@code @Transactional} previously did.
 */
@Service
public class ExpenseIngestionService {

	private final ExpenseBatchRepository batchRepository;
	private final ExpenseRepository expenseRepository;
	private final CategorizationRuleService ruleService;
	private final ExpenseCategorizationService categorizationService;
	private final ExpenseAggregationService aggregationService;
	private final TransactionTemplate transactionTemplate;

	public ExpenseIngestionService(ExpenseBatchRepository batchRepository, ExpenseRepository expenseRepository,
			CategorizationRuleService ruleService, ExpenseCategorizationService categorizationService,
			ExpenseAggregationService aggregationService, PlatformTransactionManager transactionManager) {
		this.batchRepository = batchRepository;
		this.expenseRepository = expenseRepository;
		this.ruleService = ruleService;
		this.categorizationService = categorizationService;
		this.aggregationService = aggregationService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	private record Prepared(Long batchId, String textForCategorization, List<CategorizationRule> rulesSnapshot,
			ExpenseDraftView shortCircuit) {
	}

	public ExpenseDraftView ingest(AppUser owner, SourceMode sourceMode, String rawText, List<Media> media,
			int imageCount) {
		Prepared prepared = transactionTemplate.execute(status -> prepare(owner, sourceMode, rawText, imageCount));

		if (prepared.shortCircuit() != null) {
			return prepared.shortCircuit();
		}

		CategorizationResult result = categorizationService.categorize(prepared.textForCategorization(), media,
				prepared.rulesSnapshot());

		return transactionTemplate.execute(status -> persist(owner, sourceMode, prepared.batchId(), result));
	}

	private Prepared prepare(AppUser owner, SourceMode sourceMode, String rawText, int imageCount) {
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
				&& imageCount == 0;
		if (nothingToCategorize) {
			// A standalone #newCategorizationRule message with no expense content and no images.
			batch.setStatus(ExpenseStatus.CONFIRMED);
			batchRepository.save(batch);
			return new Prepared(batch.getId(), null, rulesSnapshot, new ExpenseDraftView(batch.getId(), List.of()));
		}

		return new Prepared(batch.getId(), textForCategorization, rulesSnapshot, null);
	}

	private ExpenseDraftView persist(AppUser owner, SourceMode sourceMode, Long batchId,
			CategorizationResult result) {
		ExpenseBatch batch = batchRepository.findById(batchId).orElseThrow();
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
