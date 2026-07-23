package com.hqc.smrag.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hqc.smrag.config.ChatStreamProperties;
import com.hqc.smrag.dto.ChatRequestDTO;
import com.hqc.smrag.dto.ChatResponseDTO;
import com.hqc.smrag.dto.ChatStreamEvent;
import com.hqc.smrag.dto.CitationDTO;
import com.hqc.smrag.entity.ChatSession;
import com.hqc.smrag.enums.RouteType;
import com.hqc.smrag.exception.AiServiceException;
import com.hqc.smrag.service.DocPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
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
    private ChatStreamProperties streamProperties;

    @BeforeEach
    void setUp() {
        streamProperties = streamProperties();
        service = new AiChatService(
            queryService,
            routeService,
            knowledgeQaService,
            businessService,
            salesScriptService,
            persistenceService,
            new ObjectMapper(),
            streamProperties
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

    @Test
    void defaultsBlankKnowledgeScopeToCompanyMain() {
        ChatRequestDTO request = request();
        request.setKnowledgeBaseId(" ");
        when(routeService.route("Rewritten question")).thenReturn(RouteType.KNOWLEDGE_QA);
        when(knowledgeQaService.answer(
            "Rewritten question", "session-1", "company-main", 42L
        )).thenReturn(new KnowledgeQaResult("Grounded answer", List.of()));

        service.ask(request);

        verify(knowledgeQaService).answer(
            "Rewritten question", "session-1", "company-main", 42L
        );
    }

    @Test
    void rechecksPermissionsBeforeReturningAndPersistingCitations() {
        DocPermissionService permissionService = mock(DocPermissionService.class);
        service = new AiChatService(
            queryService,
            routeService,
            knowledgeQaService,
            businessService,
            salesScriptService,
            persistenceService,
            permissionService,
            new ObjectMapper(),
            streamProperties
        );
        CitationDTO citation = new CitationDTO(
            "vector-1", 42L, "finance", "policy.pdf", 1, 2, 0.9, "evidence"
        );
        when(permissionService.getUserRoleIds(7L)).thenReturn(List.of(5L));
        when(permissionService.getAccessibleDocIds(7L)).thenReturn(List.of());
        when(routeService.route("Rewritten question")).thenReturn(RouteType.KNOWLEDGE_QA);
        when(knowledgeQaService.answer(
            eq("Rewritten question"), eq("session-1"), eq("finance"), eq(42L), anyList()
        )).thenReturn(new KnowledgeQaResult("Grounded answer", List.of(citation)));

        ChatResponseDTO response = service.ask(request());

        assertThat(response.getCitations()).isEmpty();
        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(persistenceService).save(captor.capture());
        assertThat(captor.getValue().getReferenceDocs()).isEqualTo("[]");
        verify(knowledgeQaService).answer(
            "Rewritten question", "session-1", "finance", 42L, List.of(5L));
        verify(permissionService).getUserRoleIds(7L);
        verify(permissionService).getAccessibleDocIds(7L);
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

    @Test
    void streamsKnowledgeEventsInOrderAndPersistsOnlyTheCompletedAnswer() {
        DocPermissionService permissionService = mock(DocPermissionService.class);
        service = new AiChatService(
            queryService,
            routeService,
            knowledgeQaService,
            businessService,
            salesScriptService,
            persistenceService,
            permissionService,
            new ObjectMapper(),
            streamProperties
        );
        CitationDTO citation = new CitationDTO(
            "vector-1", 42L, "finance", "policy.pdf", 1, 2, 0.9, "evidence"
        );
        when(permissionService.getUserRoleIds(7L)).thenReturn(List.of(5L));
        when(permissionService.getAccessibleDocIds(7L)).thenReturn(List.of(42L));
        when(routeService.route("Rewritten question")).thenReturn(RouteType.KNOWLEDGE_QA);
        when(knowledgeQaService.stream(
            eq("Rewritten question"), eq("session-1"), eq("finance"), eq(42L), anyList()
        )).thenReturn(Flux.just(
            new KnowledgeQaResult("Grounded ", List.of(citation)),
            new KnowledgeQaResult("answer", List.of(citation))
        ));

        StepVerifier.create(service.stream(request()))
            .assertNext(event -> assertStatus(event, "ANALYZING"))
            .assertNext(event -> {
                assertThat(event.type()).isEqualTo("metadata");
                assertThat(event.sessionId()).isEqualTo("session-1");
                assertThat(event.routeType()).isEqualTo("KNOWLEDGE_QA");
            })
            .assertNext(event -> assertStatus(event, "RETRIEVING"))
            .assertNext(event -> assertDelta(event, "Grounded "))
            .assertNext(event -> assertDelta(event, "answer"))
            .assertNext(event -> {
                assertThat(event.type()).isEqualTo("citations");
                assertThat(event.citations()).containsExactly(citation);
                assertThat(event.references()).containsExactly("policy.pdf");
            })
            .assertNext(event -> {
                assertThat(event.type()).isEqualTo("done");
                assertThat(event.sessionId()).isEqualTo("session-1");
            })
            .verifyComplete();

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(persistenceService).save(captor.capture());
        assertThat(captor.getValue().getAnswer()).isEqualTo("Grounded answer");
        verify(knowledgeQaService).stream(
            "Rewritten question", "session-1", "finance", 42L, List.of(5L));
        verify(permissionService).getUserRoleIds(7L);
        verify(permissionService).getAccessibleDocIds(7L);
    }

    @Test
    void cancellationDoesNotPersistAPartialAnswer() {
        when(routeService.route("Rewritten question")).thenReturn(RouteType.PRODUCT_QUERY);
        when(businessService.stream(
            RouteType.PRODUCT_QUERY, "Rewritten question", "session-1"
        )).thenReturn(Flux.concat(Flux.just("partial"), Flux.never()));

        StepVerifier.create(service.stream(request()))
            .assertNext(event -> assertStatus(event, "ANALYZING"))
            .expectNextMatches(event -> event.type().equals("metadata"))
            .assertNext(event -> assertStatus(event, "CALLING_TOOL"))
            .assertNext(event -> assertDelta(event, "partial"))
            .thenCancel()
            .verify();

        verify(persistenceService, never()).save(any());
    }

    @Test
    void convertsStreamFailuresToASanitizedErrorEventWithoutPersistence() {
        when(routeService.route("Rewritten question")).thenReturn(RouteType.SALES_SCRIPT);
        when(salesScriptService.stream("Rewritten question", null, "session-1"))
            .thenReturn(Flux.error(new IllegalStateException("api-key=secret")));

        StepVerifier.create(service.stream(request()))
            .assertNext(event -> assertStatus(event, "ANALYZING"))
            .expectNextMatches(event -> event.type().equals("metadata"))
            .assertNext(event -> assertStatus(event, "GENERATING"))
            .assertNext(event -> {
                assertThat(event.type()).isEqualTo("error");
                assertThat(event.code()).isEqualTo(502);
                assertThat(event.message()).doesNotContain("secret", "api-key");
            })
            .verifyComplete();

        verify(persistenceService, never()).save(any());
    }

    @Test
    void rejectsAnEmptyCompletedStreamWithoutPersistence() {
        when(routeService.route("Rewritten question")).thenReturn(RouteType.SALES_SCRIPT);
        when(salesScriptService.stream("Rewritten question", null, "session-1"))
            .thenReturn(Flux.empty());

        StepVerifier.create(service.stream(request()))
            .assertNext(event -> assertStatus(event, "ANALYZING"))
            .expectNextMatches(event -> event.type().equals("metadata"))
            .assertNext(event -> assertStatus(event, "GENERATING"))
            .assertNext(event -> {
                assertThat(event.type()).isEqualTo("error");
                assertThat(event.code()).isEqualTo(502);
            })
            .verifyComplete();

        verify(persistenceService, never()).save(any());
    }

    @Test
    void convertsPersistenceFailuresToAnErrorWithoutDone() {
        when(routeService.route("Rewritten question")).thenReturn(RouteType.SALES_SCRIPT);
        when(salesScriptService.stream("Rewritten question", null, "session-1"))
            .thenReturn(Flux.just("complete answer"));
        doThrow(new IllegalStateException("database credentials"))
            .when(persistenceService).save(any(ChatSession.class));

        StepVerifier.create(service.stream(request()))
            .assertNext(event -> assertStatus(event, "ANALYZING"))
            .expectNextMatches(event -> event.type().equals("metadata"))
            .assertNext(event -> assertStatus(event, "GENERATING"))
            .assertNext(event -> assertDelta(event, "complete answer"))
            .assertNext(event -> {
                assertThat(event.type()).isEqualTo("error");
                assertThat(event.message()).doesNotContain("database", "credentials");
            })
            .verifyComplete();
    }

    @Test
    void timesOutAnAbandonedStreamWithoutPersistence() {
        streamProperties.setTimeout(Duration.ofMillis(20));
        when(routeService.route("Rewritten question")).thenReturn(RouteType.PRODUCT_QUERY);
        when(businessService.stream(
            RouteType.PRODUCT_QUERY, "Rewritten question", "session-1"
        )).thenReturn(Flux.never());

        StepVerifier.create(service.stream(request()))
            .assertNext(event -> assertStatus(event, "ANALYZING"))
            .expectNextMatches(event -> event.type().equals("metadata"))
            .assertNext(event -> assertStatus(event, "CALLING_TOOL"))
            .assertNext(event -> {
                assertThat(event.type()).isEqualTo("error");
                assertThat(event.code()).isEqualTo(504);
            })
            .verifyComplete();

        verify(persistenceService, never()).save(any());
    }

    private void assertStatus(ChatStreamEvent event, String phase) {
        assertThat(event.type()).isEqualTo("status");
        assertThat(event.phase()).isEqualTo(phase);
    }

    private void assertDelta(ChatStreamEvent event, String content) {
        assertThat(event.type()).isEqualTo("delta");
        assertThat(event.content()).isEqualTo(content);
    }

    private ChatStreamProperties streamProperties() {
        ChatStreamProperties properties = new ChatStreamProperties();
        properties.setTimeout(Duration.ofSeconds(30));
        return properties;
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
