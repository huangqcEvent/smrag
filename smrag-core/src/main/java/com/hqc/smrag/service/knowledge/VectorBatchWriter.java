package com.hqc.smrag.service.knowledge;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class VectorBatchWriter {

    // DashScope embedding API accepts at most 25 texts per request.
    static final int MAX_EMBEDDING_BATCH_SIZE = 25;

    private final VectorStore vectorStore;

    public void add(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        for (int start = 0; start < documents.size();
             start += MAX_EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + MAX_EMBEDDING_BATCH_SIZE, documents.size());
            vectorStore.add(documents.subList(start, end));
        }
    }
}
