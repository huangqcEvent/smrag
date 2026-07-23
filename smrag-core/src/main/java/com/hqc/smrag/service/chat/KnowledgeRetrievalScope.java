package com.hqc.smrag.service.chat;

import java.util.List;

public record KnowledgeRetrievalScope(
    Long userId,
    List<Long> knowledgeBaseIds,
    Long documentId,
    boolean globalAdmin
) {
    public KnowledgeRetrievalScope {
        knowledgeBaseIds = knowledgeBaseIds == null
            ? List.of()
            : List.copyOf(knowledgeBaseIds);
    }
}
