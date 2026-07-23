package com.hqc.smrag.service.chat;

import com.hqc.smrag.entity.ChatSession;
import com.hqc.smrag.mapper.ChatSessionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class ChatSessionPersistenceServiceTest {

    @Test
    void commitsApplicationHistoryBeforePublishingTheCompletedMemoryTurn() {
        ChatSessionMapper mapper = mock(ChatSessionMapper.class);
        ChatMemory memory = mock(ChatMemory.class);
        ChatSessionPersistenceService service = new ChatSessionPersistenceService(mapper, memory);
        ChatSession session = new ChatSession();
        session.setSessionId("session-1");
        session.setUserId(7L);
        session.setQuestion("Question");
        session.setAnswer("Answer");

        service.save(session);

        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        InOrder order = inOrder(mapper, memory);
        order.verify(mapper).insert(session);
        order.verify(memory).add(eq("7:session-1"), messages.capture());
        assertThat(messages.getValue())
            .hasSize(2)
            .satisfiesExactly(
                message -> {
                    assertThat(message).isInstanceOf(UserMessage.class);
                    assertThat(message.getText()).isEqualTo("Question");
                },
                message -> {
                    assertThat(message).isInstanceOf(AssistantMessage.class);
                    assertThat(message.getText()).isEqualTo("Answer");
                }
            );
    }
}
