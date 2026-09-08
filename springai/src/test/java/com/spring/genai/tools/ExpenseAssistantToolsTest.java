package com.spring.genai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.spring.genai.expense.Expense;
import com.spring.genai.expense.ExpenseRepository;
import com.spring.genai.expense.SourceMode;
import com.spring.genai.expense.service.ExpenseIngestionService;
import com.spring.genai.expense.service.ExpenseQueryService;
import com.spring.genai.expense.service.ExpenseRecategorizationService;
import com.spring.genai.rules.CategorizationRuleRepository;
import com.spring.genai.users.AppUser;

/** Ownership enforcement is the load-bearing safety property here: one user's chat tools must never touch another user's rows. */
class ExpenseAssistantToolsTest {

	private final ExpenseQueryService queryService = mock(ExpenseQueryService.class);
	private final ExpenseIngestionService ingestionService = mock(ExpenseIngestionService.class);
	private final ExpenseRecategorizationService recategorizationService = mock(ExpenseRecategorizationService.class);
	private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
	private final CategorizationRuleRepository ruleRepository = mock(CategorizationRuleRepository.class);
	private final ToolAuditLogger audit = mock(ToolAuditLogger.class);

	private AppUser userWithId(long id, String username) throws Exception {
		AppUser user = new AppUser(username, "hash", java.util.List.of("USER"));
		Field idField = AppUser.class.getDeclaredField("id");
		idField.setAccessible(true);
		idField.set(user, id);
		return user;
	}

	private ExpenseAssistantTools toolsFor(AppUser owner) {
		return new ExpenseAssistantTools(owner, queryService, ingestionService, recategorizationService,
				expenseRepository, ruleRepository, audit);
	}

	@Test
	void deniesEditingAnotherUsersExpense() throws Exception {
		AppUser owner = userWithId(1L, "alice");
		AppUser attacker = userWithId(2L, "mallory");
		Expense othersExpense = new Expense(owner, null, "Fruits", new BigDecimal("100"), null, SourceMode.TEXT,
				LocalDate.now());
		when(expenseRepository.findById(99L)).thenReturn(java.util.Optional.of(othersExpense));

		ExpenseAssistantTools mallorysTools = toolsFor(attacker);

		assertThatThrownBy(() -> mallorysTools.editExpense(99L, "Dining", null, null))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("404");
		verify(audit).logDenied("mallory", "editExpense", "not owner, expenseId=99");
	}

	@Test
	void discardOnlyAllowedWhilePending() throws Exception {
		AppUser owner = userWithId(1L, "alice");
		Expense confirmed = new Expense(owner, null, "Fruits", new BigDecimal("100"), null, SourceMode.TEXT,
				LocalDate.now());
		confirmed.confirm();
		when(expenseRepository.findById(5L)).thenReturn(java.util.Optional.of(confirmed));

		ExpenseAssistantTools tools = toolsFor(owner);

		assertThatThrownBy(() -> tools.discardExpenseDraft(5L)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void editingAConfirmedExpenseStampsRecategorization() throws Exception {
		AppUser owner = userWithId(1L, "alice");
		Expense confirmed = new Expense(owner, null, "Fruits", new BigDecimal("100"), null, SourceMode.TEXT,
				LocalDate.now());
		confirmed.confirm();
		when(expenseRepository.findById(7L)).thenReturn(java.util.Optional.of(confirmed));
		when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

		toolsFor(owner).editExpense(7L, "Dining", null, null);

		assertThat(confirmed.getCategory()).isEqualTo("Dining");
		assertThat(confirmed.getRecategorizedAt()).isNotNull();
	}

}
