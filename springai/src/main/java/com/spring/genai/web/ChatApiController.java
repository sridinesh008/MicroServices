package com.spring.genai.web;

import java.security.Principal;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.http.HttpStatus;

import com.spring.genai.config.AssistantProperties;
import com.spring.genai.expense.ExpenseRepository;
import com.spring.genai.expense.service.ExpenseIngestionService;
import com.spring.genai.expense.service.ExpenseQueryService;
import com.spring.genai.expense.service.ExpenseRecategorizationService;
import com.spring.genai.guardrail.PerUserRateLimiter;
import com.spring.genai.rules.CategorizationRuleRepository;
import com.spring.genai.tools.ExpenseAssistantTools;
import com.spring.genai.tools.ToolAuditLogger;
import com.spring.genai.users.AppUser;
import com.spring.genai.users.AppUserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;

@RestController
public class ChatApiController {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatApiController.class);

	private final ChatClient chatClient;
	private final PerUserRateLimiter rateLimiter;
	private final int maxInputChars;
	private final AppUserRepository appUserRepository;
	private final ExpenseQueryService expenseQueryService;
	private final ExpenseIngestionService expenseIngestionService;
	private final ExpenseRecategorizationService expenseRecategorizationService;
	private final ExpenseRepository expenseRepository;
	private final CategorizationRuleRepository ruleRepository;
	private final ToolAuditLogger toolAuditLogger;

	public ChatApiController(ChatClient chatClient, PerUserRateLimiter rateLimiter, AssistantProperties properties,
			AppUserRepository appUserRepository, ExpenseQueryService expenseQueryService,
			ExpenseIngestionService expenseIngestionService,
			ExpenseRecategorizationService expenseRecategorizationService, ExpenseRepository expenseRepository,
			CategorizationRuleRepository ruleRepository, ToolAuditLogger toolAuditLogger) {
		this.chatClient = chatClient;
		this.rateLimiter = rateLimiter;
		this.maxInputChars = properties.getGuardrail().getMaxInputChars();
		this.appUserRepository = appUserRepository;
		this.expenseQueryService = expenseQueryService;
		this.expenseIngestionService = expenseIngestionService;
		this.expenseRecategorizationService = expenseRecategorizationService;
		this.expenseRepository = expenseRepository;
		this.ruleRepository = ruleRepository;
		this.toolAuditLogger = toolAuditLogger;
	}

	@PostMapping(path = "/api/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> chat(@Valid @RequestBody ChatMessageRequest request, HttpServletRequest httpRequest,
			Principal principal) {
		String user = principal.getName();

		if (!rateLimiter.tryAcquire(user)) {
			log.warn("rate limit exceeded user={}", user);
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, slow down.");
		}
		if (request.message().length() > maxInputChars) {
			log.warn("input too large user={} length={}", user, request.message().length());
			throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
					"Message exceeds the " + maxInputChars + " character limit.");
		}

		String conversationId = httpRequest.getSession(true).getId();
		log.info("chat request user={} conversationId={} inputLength={}", user, conversationId,
				request.message().length());

		AppUser owner = appUserRepository.findByUsername(user)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
		// Built fresh per request, scoped to this user -- never shared/cached across requests.
		ExpenseAssistantTools tools = new ExpenseAssistantTools(owner, expenseQueryService, expenseIngestionService,
				expenseRecategorizationService, expenseRepository, ruleRepository, toolAuditLogger);

		return chatClient.prompt()
				.user(request.message())
				.tools(tools)
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
				.stream()
				.content();
	}

}
