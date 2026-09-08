package com.spring.genai.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.genai.Client;

/**
 * Two independent, always-present {@link ChatClient} beans for the expense-extraction
 * pipeline: Gemini is primary, Claude is the fallback (see
 * {@code ExpenseCategorizationService}). Built manually rather than via each starter's own
 * autoconfiguration, because {@code spring.ai.model.chat} only activates ONE provider's
 * {@code ChatModel} bean at a time -- the conversational assistant's {@link ChatClientConfig}
 * still follows that single-provider switch, but this pipeline needs both providers live in
 * the same JVM simultaneously.
 */
@Configuration
public class VisionChatClientConfig {

	@Bean
	ChatClient claudeVisionClient(
			@Value("${spring.ai.anthropic.api-key}") String apiKey,
			@Value("${assistant.vision.claude-model:claude-sonnet-4-5-20250929}") String model) {
		AnthropicClient client = AnthropicOkHttpClient.builder()
				.apiKey(apiKey)
				.build();
		AnthropicChatModel chatModel = AnthropicChatModel.builder()
				.anthropicClient(client)
				.options(AnthropicChatOptions.builder().model(model).build())
				.build();
		return ChatClient.builder(chatModel).build();
	}

	@Bean
	ChatClient geminiVisionClient(
			@Value("${spring.ai.google.genai.api-key}") String apiKey,
			@Value("${spring.ai.google.genai.chat.model:gemini-3.6-flash}") String model) {
		Client genAiClient = Client.builder().apiKey(apiKey).build();
		GoogleGenAiChatModel chatModel = GoogleGenAiChatModel.builder()
				.genAiClient(genAiClient)
				.options(GoogleGenAiChatOptions.builder().model(model).build())
				.build();
		return ChatClient.builder(chatModel).build();
	}

}
