package com.spring.genai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
public class ChatClientConfig {

	private static final String INJECTION_REFUSAL = "I can't act on instructions embedded in a "
			+ "message like that. Ask me something directly and I'm happy to help.";

	/**
	 * {@code chatMemoryRepository} is autoconfigured as a JDBC-backed bean by the
	 * spring-ai-starter-model-chat-memory-repository-jdbc starter (see application.yml) --
	 * survives app restarts instead of the previous in-memory default.
	 */
	@Bean
	ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository, AssistantProperties properties) {
		return MessageWindowChatMemory.builder()
				.chatMemoryRepository(chatMemoryRepository)
				.maxMessages(properties.getMemory().getMaxMessages())
				.build();
	}

	@Bean
	ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory, AssistantProperties properties) {
		return builder
				.defaultSystem(properties.getSystemPrompt())
				.defaultAdvisors(
						SafeGuardAdvisor.builder()
								.sensitiveWords(properties.getGuardrail().getBlockedPhrases())
								.failureResponse(INJECTION_REFUSAL)
								.build(),
						MessageChatMemoryAdvisor.builder(chatMemory).build())
				.build();
	}

}
