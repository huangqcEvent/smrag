package com.hqc.smrag.service.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesScriptServiceTest {

    @Test
    void usesAComplianceFocusedSystemPrompt() {
        assertThat(SalesScriptService.systemPrompt())
            .contains("\u4e0d\u627f\u8bfa\u6536\u76ca")
            .contains("\u62ab\u9732\u98ce\u9669")
            .contains("\u5efa\u8bae\u9605\u8bfb\u6b63\u5f0f\u4ea7\u54c1\u6750\u6599");
    }

    @Test
    void streamsTheCompliantAnswerWithConversationMemory() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec =
            mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.messages(any(java.util.List.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("Compliant ", "script"));
        SalesScriptService service = new SalesScriptService(
            chatClient,
            mock(ChatMemory.class)
        );

        StepVerifier.create(service.stream("Question", null, "session-1"))
            .expectNext("Compliant ", "script")
            .verifyComplete();

        verify(requestSpec).system(SalesScriptService.systemPrompt());
        verify(requestSpec).messages(any(java.util.List.class));
    }
}
