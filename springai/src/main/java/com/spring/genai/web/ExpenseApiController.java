package com.spring.genai.web;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.content.Media;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.spring.genai.config.AssistantProperties;
import com.spring.genai.expense.Expense;
import com.spring.genai.expense.ExpenseRepository;
import com.spring.genai.expense.ExpenseStatus;
import com.spring.genai.expense.SourceMode;
import com.spring.genai.expense.dto.AggregateView;
import com.spring.genai.expense.dto.EditExpenseRequest;
import com.spring.genai.expense.dto.ExpenseDraftView;
import com.spring.genai.expense.dto.ExpenseFilterRequest;
import com.spring.genai.expense.dto.ExpenseView;
import com.spring.genai.expense.dto.TextExpenseRequest;
import com.spring.genai.expense.service.ExpenseIngestionService;
import com.spring.genai.expense.service.ExpenseQueryService;
import com.spring.genai.expense.service.ExpenseRecategorizationService;
import com.spring.genai.guardrail.ExpenseRateLimiter;
import com.spring.genai.users.AppUser;
import com.spring.genai.users.AppUserRepository;

import jakarta.validation.Valid;

@RestController
public class ExpenseApiController {

	private static final Logger log = LoggerFactory.getLogger(ExpenseApiController.class);

	private final ExpenseIngestionService ingestionService;
	private final ExpenseQueryService queryService;
	private final ExpenseRecategorizationService recategorizationService;
	private final ExpenseRepository expenseRepository;
	private final AppUserRepository appUserRepository;
	private final ExpenseRateLimiter rateLimiter;
	private final AssistantProperties.Guardrail guardrail;

	public ExpenseApiController(ExpenseIngestionService ingestionService, ExpenseQueryService queryService,
			ExpenseRecategorizationService recategorizationService, ExpenseRepository expenseRepository,
			AppUserRepository appUserRepository, ExpenseRateLimiter rateLimiter, AssistantProperties properties) {
		this.ingestionService = ingestionService;
		this.queryService = queryService;
		this.recategorizationService = recategorizationService;
		this.expenseRepository = expenseRepository;
		this.appUserRepository = appUserRepository;
		this.rateLimiter = rateLimiter;
		this.guardrail = properties.getGuardrail();
	}

	@PostMapping(path = "/api/expenses/text")
	@ResponseStatus(HttpStatus.CREATED)
	public ExpenseDraftView submitText(@Valid @RequestBody TextExpenseRequest request, Principal principal) {
		AppUser user = currentUser(principal);
		checkRateLimit(user);
		if (request.message().length() > guardrail.getMaxExpenseTextChars()) {
			throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
					"Message exceeds the " + guardrail.getMaxExpenseTextChars() + " character limit.");
		}
		log.info("text expense submission user={}", user.getUsername());
		return ingestionService.ingest(user, SourceMode.TEXT, request.message(), List.of(), 0);
	}

	@PostMapping(path = "/api/expenses/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ExpenseDraftView submitImage(
			@RequestPart("images") List<MultipartFile> images,
			@RequestPart(value = "caption", required = false) String caption,
			Principal principal) {
		AppUser user = currentUser(principal);
		checkRateLimit(user);
		validateImages(images);
		List<Media> media = images.stream().map(this::toMedia).toList();
		log.info("image expense submission user={} imageCount={}", user.getUsername(), images.size());
		return ingestionService.ingest(user, SourceMode.IMAGE, caption, media, images.size());
	}

	@GetMapping("/api/expenses/drafts")
	public List<ExpenseView> drafts(Principal principal) {
		AppUser user = currentUser(principal);
		return expenseRepository.findByOwnerAndStatusOrderByCreatedAtDesc(user, ExpenseStatus.PENDING_CONFIRMATION)
				.stream().map(ExpenseView::from).toList();
	}

	@PostMapping("/api/expenses/{id}/confirm")
	public ExpenseView confirm(@PathVariable Long id, @RequestBody(required = false) EditExpenseRequest overrides,
			Principal principal) {
		Expense expense = ownedExpense(id, currentUser(principal));
		applyEdits(expense, overrides, false);
		expense.confirm();
		return ExpenseView.from(expenseRepository.save(expense));
	}

	@PatchMapping("/api/expenses/{id}")
	public ExpenseView edit(@PathVariable Long id, @RequestBody EditExpenseRequest edits, Principal principal) {
		Expense expense = ownedExpense(id, currentUser(principal));
		boolean wasConfirmed = expense.getStatus() == ExpenseStatus.CONFIRMED;
		applyEdits(expense, edits, wasConfirmed);
		return ExpenseView.from(expenseRepository.save(expense));
	}

	@PostMapping("/api/expenses/{id}/discard")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void discard(@PathVariable Long id, Principal principal) {
		Expense expense = ownedExpense(id, currentUser(principal));
		if (expense.getStatus() != ExpenseStatus.PENDING_CONFIRMATION) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a pending draft can be discarded.");
		}
		expense.discard();
		expenseRepository.save(expense);
	}

	@GetMapping("/api/expenses")
	public Page<ExpenseView> search(FilterParams params,
			@PageableDefault(size = 50, sort = "expenseDate") Pageable pageable, Principal principal) {
		AppUser user = currentUser(principal);
		return queryService.search(user, params.toFilter(), pageable).map(ExpenseView::from);
	}

	@GetMapping("/api/expenses/categories")
	public List<String> categories(Principal principal) {
		return queryService.distinctCategories(currentUser(principal));
	}

	@GetMapping("/api/expenses/aggregates")
	public AggregateView aggregates(FilterParams params, Principal principal) {
		AppUser user = currentUser(principal);
		return queryService.aggregates(user, params.toFilter());
	}

	@PostMapping("/api/expenses/reapply-rules")
	public Map<String, Integer> reapplyRules(Principal principal) {
		AppUser user = currentUser(principal);
		int updated = recategorizationService.reapplyRulesToCurrentMonth(user);
		log.info("reapply-rules user={} updatedCount={}", user.getUsername(), updated);
		return Map.of("updatedCount", updated);
	}

	// -- helpers --

	/** Binds ?dateFrom=&dateTo=&year=&month=&categories=&amountMin=&amountMax=&sourceMode=&status=&search= */
	public record FilterParams(
			LocalDate dateFrom, LocalDate dateTo, Integer year, Integer month, List<String> categories,
			BigDecimal amountMin, BigDecimal amountMax, SourceMode sourceMode, ExpenseStatus status, String search) {
		ExpenseFilterRequest toFilter() {
			return new ExpenseFilterRequest(dateFrom, dateTo, year, month, categories, amountMin, amountMax,
					sourceMode, status, search);
		}
	}

	private AppUser currentUser(Principal principal) {
		return appUserRepository.findByUsername(principal.getName())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
	}

	private Expense ownedExpense(Long id, AppUser user) {
		Expense expense = expenseRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such expense."));
		if (!expense.getOwner().getId().equals(user.getId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such expense.");
		}
		return expense;
	}

	private void applyEdits(Expense expense, EditExpenseRequest edits, boolean isPostConfirmRecategorization) {
		if (edits == null) {
			return;
		}
		if (edits.category() != null) {
			if (isPostConfirmRecategorization) {
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
		if (edits.expenseDate() != null) {
			expense.setExpenseDate(edits.expenseDate());
		}
	}

	private void checkRateLimit(AppUser user) {
		if (!rateLimiter.tryAcquire(user.getUsername())) {
			log.warn("expense rate limit exceeded user={}", user.getUsername());
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, slow down.");
		}
	}

	private void validateImages(List<MultipartFile> images) {
		if (images == null || images.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one image is required.");
		}
		if (images.size() > guardrail.getMaxImagesPerUpload()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"At most " + guardrail.getMaxImagesPerUpload() + " images per upload.");
		}
		for (MultipartFile file : images) {
			if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are accepted.");
			}
			if (file.getSize() > guardrail.getMaxImageBytes()) {
				throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
						"Image exceeds the " + guardrail.getMaxImageBytes() + " byte limit.");
			}
		}
	}

	private Media toMedia(MultipartFile file) {
		try {
			return Media.builder()
					.mimeType(MimeType.valueOf(file.getContentType()))
					.data(file.getResource())
					.build();
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded image.", ex);
		}
	}

}
