package com.spring.genai.expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.spring.genai.users.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A category-level expense row — the only thing ever persisted from an LLM extraction. Item
 * detail (e.g. "apple 1kg") never reaches this table, only the aggregated category + amount.
 */
@Entity
@Table(name = "expense")
public class Expense {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private AppUser owner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "batch_id")
	private ExpenseBatch batch;

	@Column(nullable = false, length = 100)
	private String category;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_mode", nullable = false, length = 20)
	private SourceMode sourceMode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ExpenseStatus status;

	@Column(name = "expense_date", nullable = false)
	private LocalDate expenseDate;

	@Column(name = "expense_year", nullable = false)
	private int expenseYear;

	@Column(name = "expense_month", nullable = false)
	private int expenseMonth;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "recategorized_at")
	private Instant recategorizedAt;

	@Column(name = "original_category", length = 100)
	private String originalCategory;

	protected Expense() {
	}

	public Expense(AppUser owner, ExpenseBatch batch, String category, BigDecimal amount, String description,
			SourceMode sourceMode, LocalDate expenseDate) {
		this.owner = owner;
		this.batch = batch;
		this.category = category;
		this.amount = amount;
		this.description = description;
		this.sourceMode = sourceMode;
		this.status = ExpenseStatus.PENDING_CONFIRMATION;
		this.originalCategory = category;
		setExpenseDate(expenseDate);
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void setExpenseDate(LocalDate expenseDate) {
		this.expenseDate = expenseDate;
		this.expenseYear = expenseDate.getYear();
		this.expenseMonth = expenseDate.getMonthValue();
		this.updatedAt = Instant.now();
	}

	/** Draft edit-before-confirm -- corrects the category without marking it as a post-confirm recategorization. */
	public void updateCategory(String category) {
		this.category = category;
		this.updatedAt = Instant.now();
	}

	/** Post-confirm manual or rule-driven recategorization -- stamps {@code recategorizedAt} for the audit trail. */
	public void recategorize(String newCategory) {
		this.category = newCategory;
		this.recategorizedAt = Instant.now();
		this.updatedAt = Instant.now();
	}

	public void updateAmount(BigDecimal amount) {
		this.amount = amount;
		this.updatedAt = Instant.now();
	}

	public void updateDescription(String description) {
		this.description = description;
		this.updatedAt = Instant.now();
	}

	public void confirm() {
		this.status = ExpenseStatus.CONFIRMED;
		this.updatedAt = Instant.now();
	}

	public void discard() {
		this.status = ExpenseStatus.DISCARDED;
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public AppUser getOwner() {
		return owner;
	}

	public ExpenseBatch getBatch() {
		return batch;
	}

	public String getCategory() {
		return category;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getDescription() {
		return description;
	}

	public SourceMode getSourceMode() {
		return sourceMode;
	}

	public ExpenseStatus getStatus() {
		return status;
	}

	public LocalDate getExpenseDate() {
		return expenseDate;
	}

	public int getExpenseYear() {
		return expenseYear;
	}

	public int getExpenseMonth() {
		return expenseMonth;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getRecategorizedAt() {
		return recategorizedAt;
	}

	public String getOriginalCategory() {
		return originalCategory;
	}

}
