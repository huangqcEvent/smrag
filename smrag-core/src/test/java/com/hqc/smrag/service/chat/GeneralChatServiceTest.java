package com.hqc.smrag.service.chat;

import com.hqc.smrag.exception.AiServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeneralChatServiceTest {

    @Test
    void definesDirectAnswerLimitsWithoutExternalCapabilities() {
        assertThat(GeneralChatService.systemPrompt())
            .contains("user's language")
            .contains("no access")
            .contains("real-time")
            .contains("never promise returns");
    }

    @Test
    void answersWithConversationHistoryAndWithoutTools() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec =
            mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        List<Message> history = List.of(new UserMessage("Earlier question"));
        when(chatMemory.get("7:s1")).thenReturn(history);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.messages(history)).thenReturn(requestSpec);
        when(requestSpec.user("解释复利")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("复利是利息再投资。");
        GeneralChatService service = new GeneralChatService(chatClient, chatMemory);

        String answer = service.answer("解释复利", "7:s1");

        assertThat(answer).isEqualTo("复利是利息再投资。");
        verify(requestSpec).system(GeneralChatService.systemPrompt());
        verify(requestSpec).messages(history);
        verify(requestSpec, never()).toolCallbacks(any(ToolCallback[].class));
    }

    @Test
    void streamsDirectAnswerChunks() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec =
            mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.messages(any(List.class))).thenReturn(requestSpec);
        when(requestSpec.user("解释复利")).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("复利是", "利息再投资"));
        GeneralChatService service = new GeneralChatService(chatClient, chatMemory);

        StepVerifier.create(service.stream("解释复利", "7:s1"))
            .expectNext("复利是", "利息再投资")
            .verifyComplete();

        verify(requestSpec, never()).toolCallbacks(any(ToolCallback[].class));
    }

    @Test
    void wrapsStreamingModelFailures() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec =
            mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.messages(any(List.class))).thenReturn(requestSpec);
        when(requestSpec.user("Question")).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.error(
            new IllegalStateException("provider unavailable")
        ));
        GeneralChatService service = new GeneralChatService(
            chatClient,
            mock(ChatMemory.class)
        );

        StepVerifier.create(service.stream("Question", "7:s1"))
            .expectErrorSatisfies(failure -> {
                assertThat(failure).isInstanceOf(AiServiceException.class);
                assertThat(failure).hasCauseInstanceOf(IllegalStateException.class);
            })
            .verify();
    }

    @Test
    void preservesExistingAiServiceExceptionForSyncFailures() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec =
            mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.messages(any(List.class))).thenReturn(requestSpec);
        when(requestSpec.user("Question")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        AiServiceException expected = new AiServiceException(
            "provider unavailable",
            new IllegalStateException("upstream failure")
        );
        when(callSpec.content()).thenThrow(expected);
        GeneralChatService service = new GeneralChatService(
            chatClient,
            mock(ChatMemory.class)
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.answer("Question", "7:s1")
        ).isSameAs(expected);
    }
}
