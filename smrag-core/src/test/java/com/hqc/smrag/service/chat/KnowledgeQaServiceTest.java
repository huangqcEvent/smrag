package com.hqc.smrag.service.chat;

import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.exception.AiServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeQaServiceTest {

    private final KnowledgeChatClient chatClient = mock(KnowledgeChatClient.class);
    private final RagProperties properties = properties();
    private final KnowledgeQaService service =
        new KnowledgeQaService(chatClient, new CitationAssembler(), properties);

    @Test
    void usesOneKnowledgeAnswerForContentAndCitations() {
        Document evidence = Document.builder()
            .id("vector-42")
            .text("Policy evidence")
            .metadata(Map.of("documentName", "policy.pdf"))
            .build();
        when(chatClient.answer("Question", "session-1", "finance", 42L))
            .thenReturn(new KnowledgeChatClient.KnowledgeAnswer("Grounded answer", List.of(evidence)));

        KnowledgeQaResult result = service.answer("Question", "session-1", "finance", 42L);

        assertThat(result.answer()).isEqualTo("Grounded answer");
        assertThat(result.citations()).extracting("vectorId").containsExactly("vector-42");
        verify(chatClient).answer("Question", "session-1", "finance", 42L);
    }

    @Test
    void returnsFixedRefusalWithoutFabricatedCitationsWhenContextIsEmpty() {
        when(chatClient.answer("Question", "session-1", "default", null))
            .thenReturn(new KnowledgeChatClient.KnowledgeAnswer("model output", List.of()));

        KnowledgeQaResult result = service.answer("Question", "session-1", "  ", null);

        assertThat(result.answer()).isEqualTo("\u5f53\u524d\u77e5\u8bc6\u5e93\u4e2d\u6ca1\u6709\u8db3\u591f\u8bc1\u636e\u56de\u7b54\u8be5\u95ee\u9898\u3002");
        assertThat(result.citations()).isEmpty();
        verify(chatClient).answer("Question", "session-1", "default", null);
    }

    @Test
    void convertsKnowledgeClientFailuresToStableAiServiceException() {
        when(chatClient.answer("Question", "session-1", "default", null))
            .thenThrow(new IllegalStateException("provider unavailable"));

        assertThatThrownBy(() -> service.answer("Question", "session-1", null, null))
            .isInstanceOf(AiServiceException.class)
            .hasMessage("Knowledge AI request failed")
            .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void streamsModelChunksWithCitationsFromTheRetrievedContext() {
        Document evidence = Document.builder()
            .id("vector-42")
            .text("Policy evidence")
            .metadata(Map.of("documentName", "policy.pdf"))
            .build();
        when(chatClient.stream("Question", "session-1", "finance", 42L))
            .thenReturn(Flux.just(
                new KnowledgeChatClient.KnowledgeAnswer("Grounded ", List.of(evidence)),
                new KnowledgeChatClient.KnowledgeAnswer("answer", List.of())
            ));

        StepVerifier.create(service.stream("Question", "session-1", "finance", 42L))
            .assertNext(result -> {
                assertThat(result.answer()).isEqualTo("Grounded ");
                assertThat(result.citations()).extracting("vectorId")
                    .containsExactly("vector-42");
            })
            .assertNext(result -> {
                assertThat(result.answer()).isEqualTo("answer");
                assertThat(result.citations()).extracting("vectorId")
                    .containsExactly("vector-42");
            })
            .verifyComplete();
    }

    @Test
    void replacesStreamingModelOutputWhenRetrievedContextIsEmpty() {
        when(chatClient.stream("Question", "session-1", "default", null))
            .thenReturn(Flux.just(
                new KnowledgeChatClient.KnowledgeAnswer("unsupported output", List.of()),
                new KnowledgeChatClient.KnowledgeAnswer("more output", List.of())
            ));

        StepVerifier.create(service.stream("Question", "session-1", "  ", null))
            .assertNext(result -> {
                assertThat(result.answer()).isEqualTo(KnowledgeQaService.NO_EVIDENCE_RESPONSE);
                assertThat(result.citations()).isEmpty();
            })
            .verifyComplete();
    }

    @Test
    void convertsStreamingClientFailuresToStableAiServiceException() {
        when(chatClient.stream("Question", "session-1", "default", null))
            .thenReturn(Flux.error(new IllegalStateException("provider unavailable")));

        StepVerifier.create(service.stream("Question", "session-1", null, null))
            .expectErrorSatisfies(failure -> assertThat(failure)
                .isInstanceOf(AiServiceException.class)
                .hasMessage("Knowledge AI request failed")
                .hasCauseInstanceOf(IllegalStateException.class))
            .verify();
    }

    private RagProperties properties() {
        RagProperties result = new RagProperties();
        result.setMaxContextDocuments(2);
        return result;
    }
}
