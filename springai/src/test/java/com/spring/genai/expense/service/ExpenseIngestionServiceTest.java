package com.spring.genai.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.spring.genai.expense.Expense;
import com.spring.genai.expense.ExpenseBatchRepository;
import com.spring.genai.expense.ExpenseRepository;
import com.spring.genai.expense.ExpenseStatus;
import com.spring.genai.expense.SourceMode;
import com.spring.genai.rules.CategorizationRuleService;
import com.spring.genai.users.AppUser;

import org.springframework.transaction.PlatformTransactionManager;

/** {@link ExpenseIngestionService#ingestManual} is the only path that ever creates an expense as CONFIRMED directly. */
class ExpenseIngestionServiceTest {

	private final ExpenseBatchRepository batchRepository = mock(ExpenseBatchRepository.class);
	private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
	private final CategorizationRuleService ruleService = mock(CategorizationRuleService.class);
	private final ExpenseCategorizationService categorizationService = mock(ExpenseCategorizationService.class);
	private final ExpenseAggregationService aggregationService = new ExpenseAggregationService();
	private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

	private final ExpenseIngestionService service = new ExpenseIngestionService(batchRepository, expenseRepository,
			ruleService, categorizationService, aggregationService, transactionManager);

	@Test
	void createsAConfirmedExpenseWithNoBatchAndNoLlmCall() {
		AppUser owner = new AppUser("alice", "hash", java.util.List.of("USER"));
		when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

		var view = service.ingestManual(owner, "Trader Joe's", new BigDecimal("42.50"), "Groceries",
				LocalDate.of(2026, 9, 8));

		assertThat(view.status()).isEqualTo(ExpenseStatus.CONFIRMED);
		assertThat(view.sourceMode()).isEqualTo(SourceMode.MANUAL);
		assertThat(view.category()).isEqualTo("Groceries");
		assertThat(view.description()).isEqualTo("Trader Joe's");
		assertThat(view.amount()).isEqualByComparingTo("42.50");
		assertThat(view.expenseDate()).isEqualTo(LocalDate.of(2026, 9, 8));
		assertThat(view.batchId()).isNull();
	}

	@Test
	void defaultsExpenseDateToTodayWhenOmitted() {
		AppUser owner = new AppUser("alice", "hash", java.util.List.of("USER"));
		when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

		var view = service.ingestManual(owner, "Coffee", new BigDecimal("5.00"), "Dining", null);

		assertThat(view.expenseDate()).isEqualTo(LocalDate.now());
	}

}
