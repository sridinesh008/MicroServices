package com.spring.genai.expense;

import java.time.Instant;

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

/** One user submission (an image upload of up to 3 photos, or one text message). */
@Entity
@Table(name = "expense_batch")
public class ExpenseBatch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private AppUser owner;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_mode", nullable = false, length = 20)
	private SourceMode sourceMode;

	@Column(name = "raw_user_text")
	private String rawUserText;

	@Column(name = "image_count", nullable = false)
	private int imageCount;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;

	@Column(name = "llm_provider", length = 50)
	private String llmProvider;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ExpenseStatus status;

	protected ExpenseBatch() {
	}

	public ExpenseBatch(AppUser owner, SourceMode sourceMode, String rawUserText, int imageCount) {
		this.owner = owner;
		this.sourceMode = sourceMode;
		this.rawUserText = rawUserText;
		this.imageCount = imageCount;
		this.submittedAt = Instant.now();
		this.status = ExpenseStatus.PENDING_CONFIRMATION;
	}

	public Long getId() {
		return id;
	}

	public AppUser getOwner() {
		return owner;
	}

	public SourceMode getSourceMode() {
		return sourceMode;
	}

	public String getRawUserText() {
		return rawUserText;
	}

	public int getImageCount() {
		return imageCount;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public String getLlmProvider() {
		return llmProvider;
	}

	public void setLlmProvider(String llmProvider) {
		this.llmProvider = llmProvider;
	}

	public ExpenseStatus getStatus() {
		return status;
	}

	public void setStatus(ExpenseStatus status) {
		this.status = status;
	}

}
