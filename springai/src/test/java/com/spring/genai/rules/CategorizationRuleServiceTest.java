package com.spring.genai.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.spring.genai.rules.CategorizationRuleService.RuleExtraction;

class CategorizationRuleServiceTest {

	private final CategorizationRuleService service = new CategorizationRuleService(
			mock(CategorizationRuleRepository.class));

	@Test
	void extractsStandaloneRuleMessage() {
		RuleExtraction extraction = service.extract("#newCategorizationRule fast food should be Dining");

		assertThat(extraction.hasRule()).isTrue();
		assertThat(extraction.ruleInstruction()).isEqualTo("fast food should be Dining");
		assertThat(extraction.textForCategorization()).isEmpty();
	}

	@Test
	void extractsRuleCoOccurringWithAnExpenseDescription() {
		RuleExtraction extraction = service.extract(
				"bought pizza for 300 #newCategorizationRule pizza should always be Dining");

		assertThat(extraction.hasRule()).isTrue();
		assertThat(extraction.ruleInstruction()).isEqualTo("pizza should always be Dining");
		assertThat(extraction.textForCategorization()).isEqualTo("bought pizza for 300");
	}

	@Test
	void messageWithoutTagHasNoRule() {
		RuleExtraction extraction = service.extract("bought milk for 30");

		assertThat(extraction.hasRule()).isFalse();
		assertThat(extraction.textForCategorization()).isEqualTo("bought milk for 30");
	}

	@Test
	void buildsEmptyPromptFragmentWhenNoActiveRules() {
		assertThat(service.buildRulePromptFragment(java.util.List.of())).isEmpty();
	}

}
