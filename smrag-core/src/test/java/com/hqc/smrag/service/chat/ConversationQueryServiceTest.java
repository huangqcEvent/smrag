package com.hqc.smrag.service.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationQueryServiceTest {

    private final QueryTransformer transformer = mock(QueryTransformer.class);
    private final ChatMemoryService memoryService = mock(ChatMemoryService.class);
    private final ConversationQueryService service =
        new ConversationQueryService(transformer, memoryService);

    @Test
    void returnsOriginalQuestionWhenConversationHasNoHistory() {
        when(memoryService.history("session-1")).thenReturn(List.of());

        assertThat(service.rewrite("  What is the risk level?  ", "session-1"))
            .isEqualTo("What is the risk level?");
        verify(transformer, never()).transform(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rewritesQuestionWithSpringAiQueryAndConversationHistory() {
        List<Message> history = List.of(
            new UserMessage("Tell me about Fund A"),
            new AssistantMessage("Fund A has medium risk")
        );
        when(memoryService.history("session-1")).thenReturn(history);
        when(transformer.transform(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new Query("What is the minimum investment for Fund A?"));

        String rewritten = service.rewrite("What about the minimum?", "session-1");

        assertThat(rewritten).isEqualTo("What is the minimum investment for Fund A?");
        var captor = forClass(Query.class);
        verify(transformer).transform(captor.capture());
        assertThat(captor.getValue().text()).isEqualTo("What about the minimum?");
        assertThat(captor.getValue().history()).isEqualTo(history);
    }

    @Test
    void fallsBackToOriginalQuestionWhenTransformerFails() {
        when(memoryService.history("session-1"))
            .thenReturn(List.of(new UserMessage("previous question")));
        when(transformer.transform(org.mockito.ArgumentMatchers.any()))
            .thenThrow(new IllegalStateException("model unavailable"));

        assertThat(service.rewrite("Current question", "session-1"))
            .isEqualTo("Current question");
    }
}
