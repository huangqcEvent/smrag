package com.hqc.smrag.service.chat;

import com.hqc.smrag.dto.CitationDTO;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CitationAssembler {

    private static final int MAX_EXCERPT_LENGTH = 180;

    public List<CitationDTO> assemble(List<Document> documents, int limit) {
        if (documents == null || documents.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<CitationDTO> citations = new ArrayList<>();
        Set<String> seenVectorIds = new HashSet<>();
        for (Document document : documents) {
            if (citations.size() >= limit) {
                break;
            }
            if (!seenVectorIds.add(document.getId())) {
                continue;
            }
            Map<String, Object> metadata = document.getMetadata();
            citations.add(new CitationDTO(
                document.getId(),
                asLong(metadata.get("documentId")),
                asString(metadata.get("knowledgeBaseId")),
                asString(metadata.get("documentName")),
                asInteger(metadata.get("chunkIndex")),
                asInteger(metadata.get("pageNumber")),
                document.getScore(),
                excerpt(document.getText())
            ));
        }
        return List.copyOf(citations);
    }

    private String excerpt(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_EXCERPT_LENGTH
            ? normalized
            : normalized.substring(0, MAX_EXCERPT_LENGTH);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
