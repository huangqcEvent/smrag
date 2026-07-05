package com.hqc.smrag.service.chat;

import com.hqc.smrag.service.knowledge.VectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorService vectorService;

    private static final int TOP_K = 5;

    public List<String> retrieve(String question) {
        List<Document> documents = vectorService.searchSimilar(question, TOP_K);
        List<String> contexts = documents.stream()
            .map(Document::getContent)
            .collect(Collectors.toList());

        log.info("RAG检索返回 {} 个片段", contexts.size());
        return contexts;
    }

    public List<String> retrieveReferences(String question) {
        List<Document> documents = vectorService.searchSimilar(question, TOP_K);
        return documents.stream()
            .map(doc -> (String) doc.getMetadata().get("documentName"))
            .distinct()
            .collect(Collectors.toList());
    }
}