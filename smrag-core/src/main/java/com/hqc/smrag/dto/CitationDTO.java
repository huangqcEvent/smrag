package com.hqc.smrag.dto;

public record CitationDTO(
    String vectorId,
    Long documentId,
    String knowledgeBaseId,
    String documentName,
    Integer chunkIndex,
    Integer pageNumber,
    Double score,
    String excerpt
) {
}
