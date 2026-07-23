package com.hqc.smrag.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void usesExplicitMultiKnowledgeBaseRequestAndRichCitations() throws Exception {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setSessionId("session-1");
        request.setQuestion("风险有哪些");
        request.setKnowledgeBaseCodes(List.of("research", "compliance"));
        CitationDTO citation = new CitationDTO(
            "vector-1",
            7L,
            12L,
            "research",
            "投研知识库",
            "manual.pdf",
            2,
            3,
            0.88,
            "excerpt"
        );

        String requestJson = mapper.writeValueAsString(request);
        String citationJson = mapper.writeValueAsString(citation);

        assertThat(requestJson)
            .contains("\"knowledgeBaseCodes\":[\"research\",\"compliance\"]")
            .doesNotContain("knowledgeBaseId");
        assertThat(citationJson)
            .contains("\"knowledgeBaseId\":12")
            .contains("\"knowledgeBaseName\":\"投研知识库\"");
    }
}
