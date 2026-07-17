package com.hqc.smrag.service.chat;

import org.springframework.ai.document.Document;

import java.util.List;

public interface KnowledgeChatClient {

    KnowledgeAnswer answer(
        String question,
        String sessionId,
        String knowledgeBaseId,
        Long documentId
    );

    record KnowledgeAnswer(String content, List<Document> documents) {
        public KnowledgeAnswer {
            content = content == null ? "" : content;
            documents = documents == null ? List.of() : List.copyOf(documents);
        }
    }
}
