package com.hqc.smrag.service.chat;

import com.hqc.smrag.dto.RouteDecision;
import com.hqc.smrag.enums.RouteType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteServiceTest {

    private final RouteDecisionClient decisionClient = mock(RouteDecisionClient.class);
    private final RouteService routeService = new RouteService(decisionClient);

    @Test
    void preservesStructuredManagerRoute() {
        when(decisionClient.decide("Who manages Fund A?"))
            .thenReturn(new RouteDecision(RouteType.MANAGER_QUERY, "manager details requested"));

        assertThat(routeService.route("Who manages Fund A?"))
            .isEqualTo(RouteType.MANAGER_QUERY);
    }

    @Test
    void preservesStructuredGeneralChatRoute() {
        when(decisionClient.decide("解释一下什么是复利"))
            .thenReturn(new RouteDecision(
                RouteType.GENERAL_CHAT,
                "general explanation"
            ));

        assertThat(routeService.route("解释一下什么是复利"))
            .isEqualTo(RouteType.GENERAL_CHAT);
    }

    @Test
    void fallsBackToKnowledgeQaForNullDecision() {
        when(decisionClient.decide("Question")).thenReturn(null);

        assertThat(routeService.route("Question")).isEqualTo(RouteType.KNOWLEDGE_QA);
    }

    @Test
    void fallsBackToKnowledgeQaWhenRoutingModelFails() {
        when(decisionClient.decide("Question"))
            .thenThrow(new IllegalStateException("model unavailable"));

        assertThat(routeService.route("Question")).isEqualTo(RouteType.KNOWLEDGE_QA);
    }
}
