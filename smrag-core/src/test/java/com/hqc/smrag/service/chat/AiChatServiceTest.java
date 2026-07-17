package com.hqc.smrag.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hqc.smrag.dto.ChatRequestDTO;
import com.hqc.smrag.dto.ChatResponseDTO;
import com.hqc.smrag.dto.CitationDTO;
import com.hqc.smrag.entity.ChatSession;
import com.hqc.smrag.enums.RouteType;
import com.hqc.smrag.exception.AiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServiceTest {

    private final ConversationQueryService queryService = mock(ConversationQueryService.class);
    private final RouteService routeService = mock(RouteService.class);
    private final KnowledgeQaService knowledgeQaService = mock(KnowledgeQaService.class);
    private final BusinessToolChatService businessService = mock(BusinessToolChatService.class);
    private final SalesScriptService salesScriptService = mock(SalesScriptService.class);
    private final ChatSessionPersistenceService persistenceService =
        mock(ChatSessionPersistenceService.class);
    private AiChatService service;

    @BeforeEach
    void setUp() {
        service = new AiChatService(
            queryService,
            routeService,
            knowledgeQaService,
            businessService,
            salesScriptService,
            persistenceService,
            new ObjectMapper()
        );
        when(queryService.rewrite("Original question", "session-1"))
            .thenReturn("Rewritten question");
    }

    @Test
    void orchestratesKnowledgeQaAndPersistsCitationsAsJson() {
        CitationDTO citation = new CitationDTO(
            "vector-1", 42L, "finance", "policy.pdf", 1, 2, 0.9, "evidence"
        );
        when(routeService.route("Rewritten question")).thenReturn(RouteType.KNOWLEDGE_QA);
        when(knowledgeQaService.answer(
            "Rewritten question", "session-1", "finance", 42L
        )).thenReturn(new KnowledgeQaResult("Grounded answer", List.of(citation)));

        ChatResponseDTO response = service.ask(request());

        assertThat(response.getAnswer()).isEqualTo("Grounded answer");
        assertThat(response.getCitations()).containsExactly(citation);
        assertThat(response.getReferences()).containsExactly("policy.pdf");
        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(persistenceService).save(captor.capture());
        assertThat(captor.getValue().getReferenceDocs())
            .contains("\"vectorId\":\"vector-1\"")
            .startsWith("[");
        InOrder order = inOrder(queryService, routeService, knowledgeQaService, persistenceService);
        order.verify(queryService).rewrite("Original question", "session-1");
        order.verify(routeService).route("Rewritten question");
        order.verify(knowledgeQaService).answer(
            "Rewritten question", "session-1", "finance", 42L
        );
        order.verify(persistenceService).save(any(ChatSession.class));
    }

    @ParameterizedTest
    @EnumSource(value = RouteType.class, names = {
        "PRODUCT_QUERY", "PRODUCT_COMPARE", "PRODUCT_RECOMMEND", "MANAGER_QUERY"
    })
    void delegatesBusinessRoutesToWhitelistedToolService(RouteType routeType) {
        ChatRequestDTO request = request();
        when(routeService.route("Rewritten question")).thenReturn(routeType);
        when(businessService.answer(routeType, "Rewritten question", "session-1"))
            .thenReturn("Business answer");

        ChatResponseDTO response = service.ask(request);

        assertThat(response.getAnswer()).isEqualTo("Business answer");
        assertThat(response.getCitations()).isEmpty();
        verify(businessService).answer(routeType, "Rewritten question", "session-1");
        verify(persistenceService).save(any(ChatSession.class));
    }

    @Test
    void delegatesSalesScriptWithoutBusinessTools() {
        when(routeService.route("Rewritten question")).thenReturn(RouteType.SALES_SCRIPT);
        when(salesScriptService.generate("Rewritten question", null, "session-1"))
            .thenReturn("Compliant script");

        assertThat(service.ask(request()).getAnswer()).isEqualTo("Compliant script");
        verify(salesScriptService).generate("Rewritten question", null, "session-1");
        verify(businessService, never()).answer(any(), any(), any());
    }

    @Test
    void doesNotPersistWhenFinalModelCallFails() {
        when(routeService.route("Rewritten question")).thenReturn(RouteType.PRODUCT_QUERY);
        when(businessService.answer(RouteType.PRODUCT_QUERY, "Rewritten question", "session-1"))
            .thenThrow(new AiServiceException("provider failed", new RuntimeException("secret")));

        assertThatThrownBy(() -> service.ask(request()))
            .isInstanceOf(AiServiceException.class);
        verify(persistenceService, never()).save(any());
    }

    private ChatRequestDTO request() {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setSessionId("session-1");
        request.setQuestion("Original question");
        request.setUserId(7L);
        request.setKnowledgeBaseId("finance");
        request.setDocumentId(42L);
        return request;
    }
}
