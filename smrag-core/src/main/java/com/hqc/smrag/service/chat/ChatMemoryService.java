package com.hqc.smrag.service.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMemoryService {

    private final ChatMemory chatMemory;

    public List<Message> history(String sessionId) {
        return chatMemory.get(requireSessionId(sessionId));
    }

    public void addUserMessage(String sessionId, String content) {
        chatMemory.add(requireSessionId(sessionId), new UserMessage(content));
    }

    public void addAssistantMessage(String sessionId, String content) {
        chatMemory.add(requireSessionId(sessionId), new AssistantMessage(content));
    }

    public void clear(String sessionId) {
        chatMemory.clear(requireSessionId(sessionId));
    }

    private String requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return sessionId.trim();
    }
}
