package com.hqc.smrag.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Preserves Spring AI's token limit while enforcing DashScope's per-request
 * text count limit.
 */
public final class DashScopeEmbeddingBatchingStrategy implements BatchingStrategy {

    static final int MAX_TEXTS_PER_REQUEST = 25;

    private final BatchingStrategy tokenBatchingStrategy;

    public DashScopeEmbeddingBatchingStrategy() {
        this(new TokenCountBatchingStrategy());
    }

    DashScopeEmbeddingBatchingStrategy(BatchingStrategy tokenBatchingStrategy) {
        this.tokenBatchingStrategy =
            Objects.requireNonNull(tokenBatchingStrategy, "tokenBatchingStrategy");
    }

    @Override
    public List<List<Document>> batch(List<Document> documents) {
        List<List<Document>> batches = new ArrayList<>();
        for (List<Document> tokenBatch : tokenBatchingStrategy.batch(documents)) {
            for (int start = 0; start < tokenBatch.size();
                 start += MAX_TEXTS_PER_REQUEST) {
                int end = Math.min(start + MAX_TEXTS_PER_REQUEST, tokenBatch.size());
                batches.add(List.copyOf(tokenBatch.subList(start, end)));
            }
        }
        return batches;
    }
}
