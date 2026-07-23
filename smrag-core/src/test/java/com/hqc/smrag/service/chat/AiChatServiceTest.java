package com.hqc.smrag.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hqc.smrag.config.ChatStreamProperties;
import com.hqc.smrag.dto.ChatRequestDTO;
import com.hqc.smrag.dto.ChatResponseDTO;
import com.hqc.smrag.dto.ChatStreamEvent;
import com.hqc.smrag.dto.CitationDTO;
import com.hqc.smrag.entity.ChatSession;
import com.hqc.smrag.enums.RouteType;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServiceTest {

    @Test
    void forwardsExplicitMultiBaseScopeAndPersistsTheAnswer() {
        Fixture fixture = new Fixture();
        ChatRequestDTO request = request();
        CitationDTO citation = citation();
        when(fixture.query.rewrite("原问题", "7:s1")).thenReturn("改写问题");
        when(fixture.router.route("改写问题")).thenReturn(RouteType.KNOWLEDGE_QA);
        when(fixture.knowledge.answer(
            "改写问题",
            "7:s1",
            List.of("research", "compliance"),
            null,
            7L
        )).thenReturn(new KnowledgeQaResult("回答", List.of(citation)));

        ChatResponseDTO response = fixture.service.ask(request);

        assertThat(response.getAnswer()).isEqualTo("回答");
        assertThat(response.getCitations()).containsExactly(citation);
        verify(fixture.persistence).save(any(ChatSession.class));
    }

    @Test
    void rejectsAnEmptyKnowledgeBaseScopeBeforeCallingAi() {
        Fixture fixture = new Fixture();
        ChatRequestDTO request = request();
        request.setKnowledgeBaseCodes(List.of());

        assertThatThrownBy(() -> fixture.service.ask(request))
            .isInstanceOf(IllegalArgumentException.class);
        verify(fixture.query, never()).rewrite(any(), any());
    }

    @Test
    void streamsKnowledgeAnswerAndCompletionEvents() {
        Fixture fixture = new Fixture();
        when(fixture.query.rewrite("原问题", "7:s1")).thenReturn("改写问题");
        when(fixture.router.route("改写问题")).thenReturn(RouteType.KNOWLEDGE_QA);
        when(fixture.knowledge.stream(
            eq("改写问题"),
            eq("7:s1"),
            eq(List.of("research", "compliance")),
            eq(null),
            eq(7L)
        )).thenReturn(Flux.just(
            new KnowledgeQaResult("回答", List.of(citation()))
        ));

        StepVerifier.create(fixture.service.stream(request()))
            .recordWith(java.util.ArrayList::new)
            .thenConsumeWhile(event -> true)
            .consumeRecordedWith(events -> {
                assertThat(events).extracting(ChatStreamEvent::type)
                    .contains("status", "delta", "citations", "done");
            })
            .verifyComplete();
        verify(fixture.persistence).save(any(ChatSession.class));
    }

    @Test
    void scopesTheSameClientSessionIdByAuthenticatedUser() {
        Fixture fixture = new Fixture();
        ChatRequestDTO first = request();
        ChatRequestDTO second = request();
        second.setUserId(8L);
        when(fixture.query.rewrite("原问题", "7:s1")).thenReturn("用户七的问题");
        when(fixture.query.rewrite("原问题", "8:s1")).thenReturn("用户八的问题");
        when(fixture.router.route(any())).thenReturn(RouteType.PRODUCT_QUERY);
        when(fixture.business.answer(
            RouteType.PRODUCT_QUERY, "用户七的问题", "7:s1"
        )).thenReturn("用户七的回答");
        when(fixture.business.answer(
            RouteType.PRODUCT_QUERY, "用户八的问题", "8:s1"
        )).thenReturn("用户八的回答");

        assertThat(fixture.service.ask(first).getAnswer()).isEqualTo("用户七的回答");
        assertThat(fixture.service.ask(second).getAnswer()).isEqualTo("用户八的回答");

        verify(fixture.query).rewrite("原问题", "7:s1");
        verify(fixture.query).rewrite("原问题", "8:s1");
    }

    @Test
    void removesRevokedCitationsWhenHistoryIsOpened() {
        Fixture fixture = new Fixture(true);
        ChatSession session = new ChatSession();
        session.setReferenceDocs(new ObjectMapper().valueToTree(
            List.of(citation())
        ).toString());
        when(fixture.persistence.history("s1", 7L)).thenReturn(List.of(session));
        when(fixture.permissions.getAccessibleDocIds(7L, List.of(42L)))
            .thenReturn(List.of());

        List<ChatSession> history = fixture.service.history("s1", 7L);

        assertThat(history).singleElement()
            .extracting(ChatSession::getReferenceDocs)
            .isEqualTo("[]");
    }

    private static ChatRequestDTO request() {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setSessionId("s1");
        request.setQuestion("原问题");
        request.setUserId(7L);
        request.setKnowledgeBaseCodes(List.of("research", "compliance"));
        return request;
    }

    private static CitationDTO citation() {
        return new CitationDTO(
            "v1",
            42L,
            12L,
            "research",
            "投研知识库",
            "manual.pdf",
            0,
            1,
            0.9,
            "依据"
        );
    }

    private static final class Fixture {
        private final ConversationQueryService query =
            mock(ConversationQueryService.class);
        private final RouteService router = mock(RouteService.class);
        private final KnowledgeQaService knowledge =
            mock(KnowledgeQaService.class);
        private final BusinessToolChatService business =
            mock(BusinessToolChatService.class);
        private final SalesScriptService sales = mock(SalesScriptService.class);
        private final ChatSessionPersistenceService persistence =
            mock(ChatSessionPersistenceService.class);
        private final com.hqc.smrag.service.DocPermissionService permissions =
            mock(com.hqc.smrag.service.DocPermissionService.class);
        private final ChatStreamProperties streamProperties =
            new ChatStreamProperties();
        private final AiChatService service;

        private Fixture() {
            this(false);
        }

        private Fixture(boolean withPermissionChecks) {
            streamProperties.setTimeout(Duration.ofSeconds(5));
            service = withPermissionChecks
                ? new AiChatService(
                    query,
                    router,
                    knowledge,
                    business,
                    sales,
                    persistence,
                    permissions,
                    new ObjectMapper(),
                    streamProperties
                )
                : new AiChatService(
                    query,
                    router,
                    knowledge,
                    business,
                    sales,
                    persistence,
                    new ObjectMapper(),
                    streamProperties
                );
        }
    }
}
