package com.hqc.smrag.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationQueryService {

    private final QueryTransformer queryTransformer;
    private final ChatMemoryService memoryService;

    public String rewrite(String question, String sessionId) {
        String normalizedQuestion = requireQuestion(question);
        List<Message> history = memoryService.history(sessionId);
        if (history.isEmpty()) {
            return normalizedQuestion;
        }

        try {
            Query rewritten = queryTransformer.transform(
                Query.builder()
                    .text(normalizedQuestion)
                    .history(history)
                    .build()
            );
            if (rewritten == null || rewritten.text() == null || rewritten.text().isBlank()) {
                return normalizedQuestion;
            }
            return rewritten.text().trim();
        } catch (RuntimeException failure) {
            log.warn("Unable to rewrite conversation query; using the original question", failure);
            return normalizedQuestion;
        }
    }

    private String requireQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        return question.trim();
    }
}
