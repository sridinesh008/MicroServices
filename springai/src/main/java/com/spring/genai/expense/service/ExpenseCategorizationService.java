package com.spring.genai.expense.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.spring.genai.expense.dto.CategorizedLineItems;
import com.spring.genai.rules.CategorizationRule;
import com.spring.genai.rules.CategorizationRuleService;

/**
 * Reads receipt images and/or free text into category-level line items. Claude is tried
 * first; any failure (rate limit, API error, malformed structured output) falls back to
 * Gemini. Both providers share the same prompt/DTO, so text mode (no media) and image mode
 * use one code path.
 */
@Service
public class ExpenseCategorizationService {

	private static final Logger log = LoggerFactory.getLogger(ExpenseCategorizationService.class);

	public static final String PROVIDER_ANTHROPIC = "anthropic";
	public static final String PROVIDER_GOOGLE_GENAI = "google-genai";

	private static final String BASE_INSTRUCTIONS = """
			You are an expense-tracking assistant. Read the provided receipt image(s) and/or the \
			user's text description and extract every purchased line item with its price. For \
			each item, assign a sensible spending category (e.g. Fruits, Vegetables, Milk \
			Products, Groceries, Dining, Transport, Utilities, Entertainment, Other). If a date \
			is visible on the receipt or mentioned in the text, include it as expenseDate in \
			yyyy-MM-dd format; otherwise leave expenseDate null. Respond only with the requested \
			structured data, nothing else.""";

	private final ChatClient claudeVisionClient;
	private final ChatClient geminiVisionClient;
	private final CategorizationRuleService ruleService;

	public ExpenseCategorizationService(
			@Qualifier("claudeVisionClient") ChatClient claudeVisionClient,
			@Qualifier("geminiVisionClient") ChatClient geminiVisionClient,
			CategorizationRuleService ruleService) {
		this.claudeVisionClient = claudeVisionClient;
		this.geminiVisionClient = geminiVisionClient;
		this.ruleService = ruleService;
	}

	private static final String CATEGORY_SUGGESTION_INSTRUCTIONS = """
			You are an expense-tracking assistant. Given the context below about a single \
			already-recorded expense, respond with the single best spending category label for \
			it (e.g. Fruits, Vegetables, Milk Products, Groceries, Dining, Transport, Utilities, \
			Entertainment, Other), applying the user's custom rules when they match. Respond only \
			with the requested structured data, nothing else.""";

	public record CategorizationResult(CategorizedLineItems extraction, String provider) {
	}

	private record CategorySuggestion(String category) {
	}

	public CategorizationResult categorize(String text, List<Media> media, List<CategorizationRule> activeRules) {
		String systemPrompt = BASE_INSTRUCTIONS + ruleService.buildRulePromptFragment(activeRules);

		try {
			CategorizedLineItems extraction = callVision(claudeVisionClient, systemPrompt, text, media);
			return new CategorizationResult(extraction, PROVIDER_ANTHROPIC);
		} catch (Exception primaryFailure) {
			log.warn("Claude extraction failed, falling back to Gemini", primaryFailure);
			try {
				CategorizedLineItems extraction = callVision(geminiVisionClient, systemPrompt, text, media);
				return new CategorizationResult(extraction, PROVIDER_GOOGLE_GENAI);
			} catch (Exception fallbackFailure) {
				log.error("Gemini fallback also failed", fallbackFailure);
				throw new ExtractionFailedException(
						"Could not read the expense details right now. Please try again.", fallbackFailure);
			}
		}
	}

	public String suggestCategory(String context, List<CategorizationRule> activeRules) {
		String systemPrompt = CATEGORY_SUGGESTION_INSTRUCTIONS + ruleService.buildRulePromptFragment(activeRules);
		try {
			return callSuggestion(claudeVisionClient, systemPrompt, context);
		} catch (Exception primaryFailure) {
			log.warn("Claude recategorization failed, falling back to Gemini", primaryFailure);
			try {
				return callSuggestion(geminiVisionClient, systemPrompt, context);
			} catch (Exception fallbackFailure) {
				log.error("Gemini fallback also failed", fallbackFailure);
				throw new ExtractionFailedException("Could not recategorize right now.", fallbackFailure);
			}
		}
	}

	private String callSuggestion(ChatClient client, String systemPrompt, String context) {
		return client.prompt()
				.system(systemPrompt)
				.user(context)
				.call()
				.entity(CategorySuggestion.class)
				.category();
	}

	private CategorizedLineItems callVision(ChatClient client, String systemPrompt, String text, List<Media> media) {
		return client.prompt()
				.system(systemPrompt)
				.user(u -> {
					u.text(text == null || text.isBlank() ? "Extract the expense details." : text);
					if (!media.isEmpty()) {
						u.media(media.toArray(new Media[0]));
					}
				})
				.call()
				.entity(CategorizedLineItems.class);
	}

}
