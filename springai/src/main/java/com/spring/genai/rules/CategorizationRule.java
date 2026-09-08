package com.spring.genai.rules;

import java.time.Instant;

import com.spring.genai.expense.ExpenseBatch;
import com.spring.genai.users.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A user-defined categorization instruction, extracted from a {@code #newCategorizationRule}
 * tag. Append-only: deactivated via {@link #deactivate()} rather than deleted, so past
 * categorization decisions stay explainable.
 */
@Entity
@Table(name = "categorization_rule")
public class CategorizationRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private AppUser owner;

	@Column(name = "instruction_text", nullable = false)
	private String instructionText;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "source_batch_id")
	private ExpenseBatch sourceBatch;

	protected CategorizationRule() {
	}

	public CategorizationRule(AppUser owner, String instructionText, ExpenseBatch sourceBatch) {
		this.owner = owner;
		this.instructionText = instructionText;
		this.sourceBatch = sourceBatch;
		this.active = true;
		this.createdAt = Instant.now();
	}

	public void deactivate() {
		this.active = false;
	}

	public Long getId() {
		return id;
	}

	public AppUser getOwner() {
		return owner;
	}

	public String getInstructionText() {
		return instructionText;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public ExpenseBatch getSourceBatch() {
		return sourceBatch;
	}

}
