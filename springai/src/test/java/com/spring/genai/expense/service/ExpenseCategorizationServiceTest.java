package com.spring.genai.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.spring.genai.expense.dto.CategorizedLineItems;
import com.spring.genai.expense.dto.LineItemExtraction;
import com.spring.genai.rules.CategorizationRule;
import com.spring.genai.rules.CategorizationRuleService;

class ExpenseCategorizationServiceTest {

	private final ChatClient claude = mock(ChatClient.class, RETURNS_DEEP_STUBS);
	private final ChatClient gemini = mock(ChatClient.class, RETURNS_DEEP_STUBS);
	private final CategorizationRuleService ruleService = mock(CategorizationRuleService.class);

	private final ExpenseCategorizationService service = new ExpenseCategorizationService(claude, gemini,
			ruleService);

	private final CategorizedLineItems sample = new CategorizedLineItems(
			List.of(new LineItemExtraction("apple", new BigDecimal("100"), "Fruits")), null);

	@Test
	@SuppressWarnings("unchecked")
	void usesClaudeResultWhenItSucceeds() {
		when(ruleService.buildRulePromptFragment(any())).thenReturn("");
		when(claude.prompt().system(anyString()).user(any(Consumer.class)).call()
				.entity(CategorizedLineItems.class)).thenReturn(sample);

		var result = service.categorize("apple 1kg - 100", List.of(), List.of());

		assertThat(result.provider()).isEqualTo(ExpenseCategorizationService.PROVIDER_ANTHROPIC);
		assertThat(result.extraction()).isEqualTo(sample);
	}

	@Test
	@SuppressWarnings("unchecked")
	void fallsBackToGeminiWhenClaudeFails() {
		when(ruleService.buildRulePromptFragment(any())).thenReturn("");
		when(claude.prompt().system(anyString()).user(any(Consumer.class)).call()
				.entity(CategorizedLineItems.class)).thenThrow(new RuntimeException("claude down"));
		when(gemini.prompt().system(anyString()).user(any(Consumer.class)).call()
				.entity(CategorizedLineItems.class)).thenReturn(sample);

		var result = service.categorize("apple 1kg - 100", List.of(), List.of());

		assertThat(result.provider()).isEqualTo(ExpenseCategorizationService.PROVIDER_GOOGLE_GENAI);
		assertThat(result.extraction()).isEqualTo(sample);
	}

	@Test
	@SuppressWarnings("unchecked")
	void throwsWhenBothProvidersFail() {
		when(ruleService.buildRulePromptFragment(any())).thenReturn("");
		when(claude.prompt().system(anyString()).user(any(Consumer.class)).call()
				.entity(CategorizedLineItems.class)).thenThrow(new RuntimeException("claude down"));
		when(gemini.prompt().system(anyString()).user(any(Consumer.class)).call()
				.entity(CategorizedLineItems.class)).thenThrow(new RuntimeException("gemini down"));

		assertThatThrownBy(() -> service.categorize("apple 1kg - 100", List.of(), List.of()))
				.isInstanceOf(ExtractionFailedException.class);
	}

	@Test
	@SuppressWarnings("unchecked")
	void promptIncludesActiveRuleFragment() {
		List<CategorizationRule> rules = List.of();
		when(ruleService.buildRulePromptFragment(rules)).thenReturn("\n\nCustom rule: pizza is Dining");
		when(claude.prompt().system(anyString()).user(any(Consumer.class)).call()
				.entity(CategorizedLineItems.class)).thenReturn(sample);

		service.categorize("pizza 300", List.of(), rules);

		// atLeastOnce(): the when(...) stub setup above also invokes .system(...) once on the
		// same deep-stub chain; .getValue() returns the last (real) invocation's argument.
		org.mockito.ArgumentCaptor<String> systemPrompt = org.mockito.ArgumentCaptor.forClass(String.class);
		org.mockito.Mockito.verify(claude.prompt(), org.mockito.Mockito.atLeastOnce()).system(systemPrompt.capture());
		assertThat(systemPrompt.getValue()).contains("Custom rule: pizza is Dining");
	}

}
