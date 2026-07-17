package com.hqc.smrag.service.knowledge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorService {

    private final VectorStore vectorStore;

    public void storeVector(String content, Map<String, Object> metadata) {
        Document document = new Document(content, metadata);
        vectorStore.add(List.of(document));
        log.info("存储向量完成");
    }

    public void storeVectors(List<String> contents, Map<String, Object> baseMetadata) {
        List<Document> documents = contents.stream()
            .map(content -> new Document(content, baseMetadata))
            .collect(Collectors.toList());
        vectorStore.add(documents);
        log.info("批量存储向量完成，数量: {}", documents.size());
    }

    public List<Document> searchSimilar(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .build();
        List<Document> results = vectorStore.similaritySearch(request);
        log.info("搜索完成，返回结果数: {}", results.size());
        return results;
    }
}
