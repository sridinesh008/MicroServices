package com.spring.genai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

class ChatMemoryTest {

	@Test
	void windowTruncatesToMaxMessages() {
		ChatMemory memory = MessageWindowChatMemory.builder()
				.chatMemoryRepository(new InMemoryChatMemoryRepository())
				.maxMessages(2)
				.build();

		memory.add("conv-1", new UserMessage("first"));
		memory.add("conv-1", new UserMessage("second"));
		memory.add("conv-1", new UserMessage("third"));

		List<Message> stored = memory.get("conv-1");
		assertThat(stored).hasSize(2);
		assertThat(stored.get(0).getText()).isEqualTo("second");
		assertThat(stored.get(1).getText()).isEqualTo("third");
	}

	@Test
	void conversationsAreIsolatedById() {
		ChatMemory memory = MessageWindowChatMemory.builder()
				.chatMemoryRepository(new InMemoryChatMemoryRepository())
				.maxMessages(20)
				.build();

		memory.add("conv-a", new UserMessage("hello from a"));
		memory.add("conv-b", new UserMessage("hello from b"));

		assertThat(memory.get("conv-a")).extracting(Message::getText).containsExactly("hello from a");
		assertThat(memory.get("conv-b")).extracting(Message::getText).containsExactly("hello from b");
	}
}
