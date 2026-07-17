package com.hqc.smrag.service.chat;

import com.hqc.smrag.entity.ChatSession;
import com.hqc.smrag.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatSessionPersistenceService {

    private final ChatSessionMapper sessionMapper;

    @Transactional
    public void save(ChatSession session) {
        sessionMapper.insert(session);
    }

    @Transactional(readOnly = true)
    public List<ChatSession> history(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return sessionMapper.findBySessionId(sessionId.trim());
    }
}
