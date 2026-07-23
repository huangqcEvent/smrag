package com.hqc.smrag.service.chat;

import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

import java.util.List;

public interface KnowledgeChatClient {

    KnowledgeAnswer answer(
        String question,
        String sessionId,
        KnowledgeRetrievalScope scope
    );

    Flux<KnowledgeAnswer> stream(
        String question,
        String sessionId,
        KnowledgeRetrievalScope scope
    );

    record KnowledgeAnswer(String content, List<Document> documents) {
        public KnowledgeAnswer {
            content = content == null ? "" : content;
            documents = documents == null ? List.of() : List.copyOf(documents);
        }
    }
}
