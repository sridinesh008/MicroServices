package com.spring.genai.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.spring.genai.config.AssistantProperties;
import com.spring.genai.expense.ExpenseRepository;
import com.spring.genai.expense.service.ExpenseIngestionService;
import com.spring.genai.expense.service.ExpenseQueryService;
import com.spring.genai.expense.service.ExpenseRecategorizationService;
import com.spring.genai.guardrail.PerUserRateLimiter;
import com.spring.genai.rules.CategorizationRuleRepository;
import com.spring.genai.tools.ToolAuditLogger;
import com.spring.genai.users.AppUserRepository;
import com.spring.genai.users.JpaUserDetailsService;
import com.spring.genai.web.ChatApiController;
import com.spring.genai.web.GlobalExceptionHandler;

@WebMvcTest(controllers = { ChatApiController.class })
@org.springframework.context.annotation.Import({ SecurityConfig.class, GlobalExceptionHandler.class,
		JpaUserDetailsService.class })
@EnableConfigurationProperties(AssistantProperties.class)
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ChatClient chatClient;

	@MockitoBean
	private PerUserRateLimiter rateLimiter;

	@MockitoBean
	private AppUserRepository appUserRepository;

	@MockitoBean
	private ExpenseQueryService expenseQueryService;

	@MockitoBean
	private ExpenseIngestionService expenseIngestionService;

	@MockitoBean
	private ExpenseRecategorizationService expenseRecategorizationService;

	@MockitoBean
	private ExpenseRepository expenseRepository;

	@MockitoBean
	private CategorizationRuleRepository categorizationRuleRepository;

	@MockitoBean
	private ToolAuditLogger toolAuditLogger;

	// "/" is now the bundled React SPA's index.html (frontend/dist, copied into
	// target/classes/static by the frontend-maven-plugin build before tests run), served as
	// Boot's static welcome page -- not a @Controller, so it isn't in this slice's
	// `controllers = {...}` list, but Spring's static resource handling still serves it.

	@Test
	void anonymousIsRedirectedAwayFromTheSpaShell() throws Exception {
		mockMvc.perform(get("/").accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());
	}

	@Test
	void authenticatedUserCanLoadTheSpaShell() throws Exception {
		mockMvc.perform(get("/").accept(MediaType.TEXT_HTML).with(user("user")))
				.andExpect(status().isOk());
	}

	@Test
	void chatApiWithoutCsrfTokenIsForbidden() throws Exception {
		mockMvc.perform(post("/api/chat")
				.with(user("user"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"hi\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void securityHeadersArePresentOnEveryResponse() throws Exception {
		// .secure(true) simulates the request as HTTPS, as it would arrive after ALB
		// termination in prod — Spring Security only emits HSTS over a secure request.
		mockMvc.perform(get("/").accept(MediaType.TEXT_HTML).secure(true).with(user("user")))
				.andExpect(header().exists("Strict-Transport-Security"))
				.andExpect(header().exists("Content-Security-Policy"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("X-Frame-Options", "DENY"));
	}
}
