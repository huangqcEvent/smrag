package com.hqc.smrag.dto;

import com.hqc.smrag.enums.RouteType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatContractTest {

    @Test
    void supportsKnowledgeScopeAndStructuredCitations() {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setSessionId("s-1");
        request.setQuestion("这只基金的风险是什么？");
        request.setKnowledgeBaseId("default");
        request.setDocumentId(11L);

        CitationDTO citation = new CitationDTO(
            "vector-id",
            11L,
            "default",
            "招募说明书.pdf",
            4,
            3,
            0.91d,
            "风险等级为中风险"
        );
        ChatResponseDTO response = new ChatResponseDTO();
        response.setAnswer("回答");
        response.setRouteType(RouteType.KNOWLEDGE_QA.name());
        response.setReferences(List.of("招募说明书.pdf"));
        response.setCitations(List.of(citation));
        response.setSessionId("s-1");

        assertThat(request.getKnowledgeBaseId()).isEqualTo("default");
        assertThat(request.getDocumentId()).isEqualTo(11L);
        assertThat(response.getReferences()).containsExactly("招募说明书.pdf");
        assertThat(response.getCitations()).containsExactly(citation);
        assertThat(RouteType.valueOf("MANAGER_QUERY")).isEqualTo(RouteType.MANAGER_QUERY);
    }

    @Test
    void routeDecisionFallsBackToKnowledgeQa() {
        assertThat(new RouteDecision(null, null))
            .isEqualTo(new RouteDecision(RouteType.KNOWLEDGE_QA, ""));
    }
}
