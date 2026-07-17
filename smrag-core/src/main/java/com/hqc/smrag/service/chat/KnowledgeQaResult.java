package com.hqc.smrag.service.chat;

import com.hqc.smrag.dto.CitationDTO;

import java.util.List;

public record KnowledgeQaResult(String answer, List<CitationDTO> citations) {
    public KnowledgeQaResult {
        answer = answer == null ? "" : answer;
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
