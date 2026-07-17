package com.hqc.smrag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatStreamEvent(
    String type,
    String phase,
    String sessionId,
    String routeType,
    String content,
    List<CitationDTO> citations,
    List<String> references,
    Integer code,
    String message
) {

    public ChatStreamEvent {
        citations = citations == null ? null : List.copyOf(citations);
        references = references == null ? null : List.copyOf(references);
    }

    public static ChatStreamEvent status(String phase) {
        return new ChatStreamEvent("status", phase, null, null, null, null, null, null, null);
    }

    public static ChatStreamEvent metadata(String sessionId, String routeType) {
        return new ChatStreamEvent(
            "metadata", null, sessionId, routeType, null, null, null, null, null
        );
    }

    public static ChatStreamEvent delta(String content) {
        return new ChatStreamEvent("delta", null, null, null, content, null, null, null, null);
    }

    public static ChatStreamEvent citations(
        List<CitationDTO> citations,
        List<String> references
    ) {
        return new ChatStreamEvent(
            "citations", null, null, null, null, citations, references, null, null
        );
    }

    public static ChatStreamEvent done(String sessionId, String routeType) {
        return new ChatStreamEvent(
            "done", null, sessionId, routeType, null, null, null, null, null
        );
    }

    public static ChatStreamEvent error(int code, String message) {
        return new ChatStreamEvent("error", null, null, null, null, null, null, code, message);
    }
}
