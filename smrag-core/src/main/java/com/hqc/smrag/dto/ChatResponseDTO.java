package com.hqc.smrag.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChatResponseDTO {
    private String answer;
    private String routeType;
    private List<String> references;
    private List<CitationDTO> citations = List.of();
    private String sessionId;
}
