package com.spring.genai.rules;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spring.genai.expense.ExpenseBatch;
import com.spring.genai.users.AppUser;

/**
 * Extracts and persists {@code #newCategorizationRule} instructions, and builds the
 * active-rule prompt fragment injected into every categorization LLM call.
 *
 * <p>The tag can co-occur with an expense description in the same message. The new rule must
 * NOT apply to that same message's own categorization -- callers must fetch
 * {@link #activeRulesSnapshot(AppUser)} <em>before</em> calling {@link #extractAndPersist} so
 * the categorization call uses the rule set as it existed just before this new rule.
 */
@Service
public class CategorizationRuleService {

	private static final Logger log = LoggerFactory.getLogger(CategorizationRuleService.class);

	private static final Pattern TAG_PATTERN = Pattern.compile(
			"#newCategorizationRule\\s*(.*?)(?=\\s*#\\w|$)", Pattern.DOTALL);

	private final CategorizationRuleRepository repository;

	public CategorizationRuleService(CategorizationRuleRepository repository) {
		this.repository = repository;
	}

	/** Rule text found (if any), and the message with the tag+instruction stripped out. */
	public record RuleExtraction(String textForCategorization, String ruleInstruction) {
		public boolean hasRule() {
			return ruleInstruction != null && !ruleInstruction.isBlank();
		}
	}

	public RuleExtraction extract(String rawText) {
		if (rawText == null) {
			return new RuleExtraction(null, null);
		}
		Matcher matcher = TAG_PATTERN.matcher(rawText);
		if (!matcher.find()) {
			return new RuleExtraction(rawText, null);
		}
		String instruction = matcher.group(1).trim();
		String remaining = (rawText.substring(0, matcher.start()) + rawText.substring(matcher.end())).trim();
		return new RuleExtraction(remaining, instruction);
	}

	public List<CategorizationRule> activeRulesSnapshot(AppUser owner) {
		return repository.findByOwnerAndActiveTrue(owner);
	}

	@Transactional
	public CategorizationRule persist(AppUser owner, String instructionText, ExpenseBatch sourceBatch) {
		CategorizationRule rule = repository.save(new CategorizationRule(owner, instructionText, sourceBatch));
		log.info("new categorization rule owner={} ruleId={}", owner.getUsername(), rule.getId());
		return rule;
	}

	public String buildRulePromptFragment(List<CategorizationRule> activeRules) {
		if (activeRules.isEmpty()) {
			return "";
		}
		String rulesList = activeRules.stream()
				.map(r -> "- " + r.getInstructionText())
				.collect(Collectors.joining("\n"));
		return "\n\nThe user has defined these custom categorization rules. Apply them when they "
				+ "clearly match; otherwise use sensible default categories:\n" + rulesList;
	}

}
