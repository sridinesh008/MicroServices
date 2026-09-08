package com.spring.genai.tools;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.spring.genai.expense.Expense;
import com.spring.genai.expense.ExpenseRepository;
import com.spring.genai.expense.ExpenseStatus;
import com.spring.genai.expense.SourceMode;
import com.spring.genai.expense.dto.EditExpenseRequest;
import com.spring.genai.expense.dto.ExpenseDraftView;
import com.spring.genai.expense.dto.ExpenseFilterRequest;
import com.spring.genai.expense.dto.ExpenseView;
import com.spring.genai.expense.service.ExpenseIngestionService;
import com.spring.genai.expense.service.ExpenseQueryService;
import com.spring.genai.expense.service.ExpenseRecategorizationService;
import com.spring.genai.rules.CategorizationRule;
import com.spring.genai.rules.CategorizationRuleRepository;
import com.spring.genai.rules.dto.RuleView;
import com.spring.genai.users.AppUser;

/**
 * The assistant's full data-access surface: one method per distinct action, each named,
 * scoped to {@code owner}, and audit-logged individually via {@link ToolAuditLogger} -- so
 * every chat-driven read or write against a user's financial data has its own trail. Every
 * method enforces ownership itself; there is no cross-user access path here.
 *
 * <p>Deliberately excludes a "confirm expense" tool: finalizing a draft into a real record
 * stays a human action in the UI, never something the assistant can do on its own say-so.
 *
 * <p>Built fresh per chat request (see {@code ChatApiController}), not a shared Spring bean --
 * {@code owner} is fixed at construction time so it can never leak across users.
 */
public class ExpenseAssistantTools {

	private final AppUser owner;
	private final ExpenseQueryService queryService;
	private final ExpenseIngestionService ingestionService;
	private final ExpenseRecategorizationService recategorizationService;
	private final ExpenseRepository expenseRepository;
	private final CategorizationRuleRepository ruleRepository;
	private final ToolAuditLogger audit;

	public ExpenseAssistantTools(AppUser owner, ExpenseQueryService queryService,
			ExpenseIngestionService ingestionService, ExpenseRecategorizationService recategorizationService,
			ExpenseRepository expenseRepository, CategorizationRuleRepository ruleRepository,
			ToolAuditLogger audit) {
		this.owner = owner;
		this.queryService = queryService;
		this.ingestionService = ingestionService;
		this.recategorizationService = recategorizationService;
		this.expenseRepository = expenseRepository;
		this.ruleRepository = ruleRepository;
		this.audit = audit;
	}

	@Tool(name = "getCurrentDate", description = "Returns today's date (yyyy-MM-dd). Call this "
			+ "first whenever you need to compute a relative date range like 'last 3 days'.")
	public String getCurrentDate() {
		audit.logInvocation(owner.getUsername(), "getCurrentDate", "");
		return LocalDate.now().toString();
	}

	@Tool(name = "listExpenses", description = "Lists the user's confirmed expenses, optionally "
			+ "filtered by date range and/or category. Dates are yyyy-MM-dd.")
	public List<ExpenseView> listExpenses(
			@ToolParam(description = "Inclusive start date, or null for no lower bound", required = false) LocalDate dateFrom,
			@ToolParam(description = "Inclusive end date, or null for no upper bound", required = false) LocalDate dateTo,
			@ToolParam(description = "Category names to filter by, or null for all", required = false) List<String> categories) {
		audit.logInvocation(owner.getUsername(), "listExpenses",
				"dateFrom=" + dateFrom + " dateTo=" + dateTo + " categories=" + categories);
		List<Expense> results = queryService.searchAll(owner, filter(dateFrom, dateTo, categories));
		audit.logOutcome(owner.getUsername(), "listExpenses", results.size() + " rows");
		return results.stream().map(ExpenseView::from).toList();
	}

	@Tool(name = "sumExpenses", description = "Returns the total amount spent, optionally "
			+ "filtered by date range and/or category. Use this for 'how much did I spend on X' "
			+ "questions rather than summing listExpenses yourself.")
	public BigDecimal sumExpenses(
			@ToolParam(description = "Inclusive start date, or null for no lower bound", required = false) LocalDate dateFrom,
			@ToolParam(description = "Inclusive end date, or null for no upper bound", required = false) LocalDate dateTo,
			@ToolParam(description = "Category names to filter by, or null for all", required = false) List<String> categories) {
		audit.logInvocation(owner.getUsername(), "sumExpenses",
				"dateFrom=" + dateFrom + " dateTo=" + dateTo + " categories=" + categories);
		BigDecimal total = queryService.sumAmount(owner, filter(dateFrom, dateTo, categories));
		audit.logOutcome(owner.getUsername(), "sumExpenses", total.toString());
		return total;
	}

	@Tool(name = "listCategories", description = "Lists every category the user currently has confirmed expenses in.")
	public List<String> listCategories() {
		audit.logInvocation(owner.getUsername(), "listCategories", "");
		return queryService.distinctCategories(owner);
	}

	@Tool(name = "listCategorizationRules", description = "Lists the user's custom categorization rules, active and inactive.")
	public List<RuleView> listCategorizationRules() {
		audit.logInvocation(owner.getUsername(), "listCategorizationRules", "");
		return ruleRepository.findByOwnerOrderByCreatedAtDesc(owner).stream().map(RuleView::from).toList();
	}

	@Tool(name = "logTextExpense", description = "Records a new expense from a natural-language "
			+ "description (e.g. 'apple 1kg 100rs, milk 1lt 30rs'). This only creates a PENDING "
			+ "draft -- the user must still review and confirm it in the app before it counts as spent.")
	public ExpenseDraftView logTextExpense(@ToolParam(description = "The expense description") String message) {
		audit.logInvocation(owner.getUsername(), "logTextExpense", message);
		ExpenseDraftView draft = ingestionService.ingest(owner, SourceMode.TEXT, message, List.of(), 0);
		audit.logOutcome(owner.getUsername(), "logTextExpense", "batchId=" + draft.batchId());
		return draft;
	}

	@Tool(name = "discardExpenseDraft", description = "Discards a pending (not yet confirmed) expense draft by id.")
	public String discardExpenseDraft(@ToolParam(description = "The expense id to discard") Long expenseId) {
		audit.logInvocation(owner.getUsername(), "discardExpenseDraft", "expenseId=" + expenseId);
		Expense expense = ownedExpense(expenseId, "discardExpenseDraft");
		if (expense.getStatus() != ExpenseStatus.PENDING_CONFIRMATION) {
			audit.logDenied(owner.getUsername(), "discardExpenseDraft", "not pending, id=" + expenseId);
			throw new IllegalStateException("Only a pending draft can be discarded.");
		}
		expense.discard();
		expenseRepository.save(expense);
		audit.logOutcome(owner.getUsername(), "discardExpenseDraft", "discarded id=" + expenseId);
		return "Discarded expense " + expenseId + ".";
	}

	@Tool(name = "editExpense", description = "Edits a pending draft's or already-confirmed "
			+ "expense's category, amount, and/or description. Pass null for any field to leave "
			+ "it unchanged. On an already-confirmed expense this is a recategorization and is "
			+ "recorded as such.")
	public ExpenseView editExpense(
			@ToolParam(description = "The expense id to edit") Long expenseId,
			@ToolParam(description = "New category, or null to leave unchanged", required = false) String category,
			@ToolParam(description = "New amount, or null to leave unchanged", required = false) BigDecimal amount,
			@ToolParam(description = "New description, or null to leave unchanged", required = false) String description) {
		audit.logInvocation(owner.getUsername(), "editExpense",
				"expenseId=" + expenseId + " category=" + category + " amount=" + amount);
		Expense expense = ownedExpense(expenseId, "editExpense");
		boolean wasConfirmed = expense.getStatus() == ExpenseStatus.CONFIRMED;
		EditExpenseRequest edits = new EditExpenseRequest(category, amount, description, null);
		if (edits.category() != null) {
			if (wasConfirmed) {
				expense.recategorize(edits.category());
			} else {
				expense.updateCategory(edits.category());
			}
		}
		if (edits.amount() != null) {
			expense.updateAmount(edits.amount());
		}
		if (edits.description() != null) {
			expense.updateDescription(edits.description());
		}
		ExpenseView view = ExpenseView.from(expenseRepository.save(expense));
		audit.logOutcome(owner.getUsername(), "editExpense", "id=" + expenseId + " newCategory=" + view.category());
		return view;
	}

	@Tool(name = "createCategorizationRule", description = "Adds a new custom categorization "
			+ "rule that future expense categorization will follow (equivalent to the "
			+ "#newCategorizationRule tag).")
	public RuleView createCategorizationRule(@ToolParam(description = "The rule instruction") String instructionText) {
		audit.logInvocation(owner.getUsername(), "createCategorizationRule", instructionText);
		CategorizationRule rule = ruleRepository.save(new CategorizationRule(owner, instructionText, null));
		audit.logOutcome(owner.getUsername(), "createCategorizationRule", "ruleId=" + rule.getId());
		return RuleView.from(rule);
	}

	@Tool(name = "deactivateCategorizationRule", description = "Deactivates one of the user's custom categorization rules by id.")
	public String deactivateCategorizationRule(@ToolParam(description = "The rule id to deactivate") Long ruleId) {
		audit.logInvocation(owner.getUsername(), "deactivateCategorizationRule", "ruleId=" + ruleId);
		CategorizationRule rule = ruleRepository.findById(ruleId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such rule."));
		if (!rule.getOwner().getId().equals(owner.getId())) {
			audit.logDenied(owner.getUsername(), "deactivateCategorizationRule", "not owner, ruleId=" + ruleId);
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such rule.");
		}
		rule.deactivate();
		ruleRepository.save(rule);
		audit.logOutcome(owner.getUsername(), "deactivateCategorizationRule", "deactivated ruleId=" + ruleId);
		return "Deactivated rule " + ruleId + ".";
	}

	@Tool(name = "reapplyRulesToCurrentMonth", description = "Re-runs the user's active "
			+ "categorization rules against this calendar month's already-confirmed expenses "
			+ "only. Never touches other months.")
	public String reapplyRulesToCurrentMonth() {
		audit.logInvocation(owner.getUsername(), "reapplyRulesToCurrentMonth", "");
		int updated = recategorizationService.reapplyRulesToCurrentMonth(owner);
		audit.logOutcome(owner.getUsername(), "reapplyRulesToCurrentMonth", "updatedCount=" + updated);
		return "Updated " + updated + " expense(s) this month.";
	}

	private Expense ownedExpense(Long id, String toolName) {
		Expense expense = expenseRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such expense."));
		if (!expense.getOwner().getId().equals(owner.getId())) {
			audit.logDenied(owner.getUsername(), toolName, "not owner, expenseId=" + id);
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such expense.");
		}
		return expense;
	}

	private ExpenseFilterRequest filter(LocalDate dateFrom, LocalDate dateTo, List<String> categories) {
		return new ExpenseFilterRequest(dateFrom, dateTo, null, null, categories, null, null, null, null, null);
	}

}
