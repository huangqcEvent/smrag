package com.hqc.smrag.config;

import com.hqc.smrag.service.chat.ChatMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAIConfigTest {

    @Test
    void keepsOnlyConfiguredMessagesPerConversation() {
        RagProperties properties = new RagProperties();
        properties.setMemoryWindow(12);
        ChatMemory memory = new SpringAIConfig().chatMemory(
            new InMemoryChatMemoryRepository(),
            properties
        );
        ChatMemoryService service = new ChatMemoryService(memory);

        IntStream.range(0, 14)
            .forEach(i -> service.addUserMessage("s-1", "q-" + i));
        service.addUserMessage("s-2", "isolated");

        assertThat(service.history("s-1")).hasSize(12);
        assertThat(service.history("s-1").get(0).getText()).isEqualTo("q-2");
        assertThat(service.history("s-2"))
            .singleElement()
            .isEqualTo(new UserMessage("isolated"));
    }

    @Test
    void rejectsBlankConversationIds() {
        ChatMemory memory = new SpringAIConfig().chatMemory(
            new InMemoryChatMemoryRepository(),
            new RagProperties()
        );
        ChatMemoryService service = new ChatMemoryService(memory);

        assertThatThrownBy(() -> service.history("  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sessionId");
        assertThatThrownBy(() -> service.clear(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sessionId");
    }
}
