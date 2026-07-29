package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.enums.ChunkingStrategy;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Splits source documents while retaining their source metadata.
 */
public interface DocumentSplittingStrategy {

    ChunkingStrategy type();

    List<Document> split(List<Document> documents);
}
