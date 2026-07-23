package com.hqc.smrag.service.chat;

import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.mapper.AuthorizedVectorMapper;
import com.hqc.smrag.mapper.projection.AuthorizedVectorRow;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthorizedPgVectorRetriever {

    private final EmbeddingModel embeddingModel;
    private final AuthorizedVectorMapper mapper;
    private final RagProperties properties;

    public List<Document> retrieve(
        String question,
        KnowledgeRetrievalScope scope
    ) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("检索问题不能为空");
        }
        if (scope == null || scope.userId() == null
            || scope.knowledgeBaseIds().isEmpty()) {
            return List.of();
        }
        float[] embedding = embeddingModel.embed(question);
        Map<String, AuthorizedVectorRow> bestByVector = new LinkedHashMap<>();
        for (Long knowledgeBaseId : scope.knowledgeBaseIds()) {
            for (AuthorizedVectorRow row : mapper.search(
                knowledgeBaseId,
                scope.userId(),
                scope.globalAdmin(),
                scope.documentId(),
                embedding,
                properties.getSimilarityThreshold(),
                properties.getTopK()
            )) {
                bestByVector.merge(
                    row.vectorId(),
                    row,
                    (left, right) -> left.score() >= right.score() ? left : right
                );
            }
        }
        return bestByVector.values().stream()
            .sorted(Comparator.comparing(
                AuthorizedVectorRow::score,
                Comparator.nullsLast(Comparator.reverseOrder())
            ))
            .limit(properties.getTopK())
            .map(this::toDocument)
            .toList();
    }

    private Document toDocument(AuthorizedVectorRow row) {
        Map<String, Object> metadata = new LinkedHashMap<>(row.metadata());
        metadata.put("knowledgeBaseId", row.knowledgeBaseId());
        metadata.put("knowledgeBaseCode", row.knowledgeBaseCode());
        metadata.put("knowledgeBaseName", row.knowledgeBaseName());
        return Document.builder()
            .id(row.vectorId())
            .text(row.content())
            .metadata(metadata)
            .score(row.score())
            .build();
    }
}
