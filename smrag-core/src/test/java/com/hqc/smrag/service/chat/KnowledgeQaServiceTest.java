package com.hqc.smrag.service.chat;

import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.entity.KnowledgeBase;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.service.knowledge.KnowledgeBaseService;
import com.hqc.smrag.service.knowledge.PermissionDecisionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeQaServiceTest {

    @Test
    void resolvesAndAuthorizesEverySelectedBaseBeforeRetrieval() {
        Fixture fixture = new Fixture();
        when(fixture.bases.requireActive("research"))
            .thenReturn(base(12L, "research"));
        when(fixture.bases.requireActive("compliance"))
            .thenReturn(base(18L, "compliance"));
        when(fixture.client.answer(eq("问题"), eq("s1"), any()))
            .thenReturn(new KnowledgeChatClient.KnowledgeAnswer(
                "回答",
                List.of(new Document(
                    "v1",
                    "依据",
                    java.util.Map.of(
                        "documentId", 7L,
                        "knowledgeBaseId", 12L,
                        "knowledgeBaseCode", "research",
                        "knowledgeBaseName", "投研知识库",
                        "documentName", "manual.pdf",
                        "chunkIndex", 0
                    )
                ))
            ));

        KnowledgeQaResult result = fixture.service.answer(
            "问题",
            "s1",
            List.of("research", "compliance"),
            null,
            7L
        );

        ArgumentCaptor<KnowledgeRetrievalScope> scope =
            ArgumentCaptor.forClass(KnowledgeRetrievalScope.class);
        verify(fixture.client).answer(eq("问题"), eq("s1"), scope.capture());
        assertThat(scope.getValue().knowledgeBaseIds())
            .containsExactly(12L, 18L);
        verify(fixture.decisions)
            .requireKnowledgeBaseAccess(7L, List.of(12L, 18L));
        assertThat(result.citations()).hasSize(1);
    }

    @Test
    void returnsNoEvidenceWhenAuthorizedRetrievalReturnsNothing() {
        Fixture fixture = new Fixture();
        when(fixture.bases.requireActive("research"))
            .thenReturn(base(12L, "research"));
        when(fixture.client.answer(any(), any(), any()))
            .thenReturn(new KnowledgeChatClient.KnowledgeAnswer("ignored", List.of()));

        KnowledgeQaResult result = fixture.service.answer(
            "问题", "s1", List.of("research"), null, 7L);

        assertThat(result.answer())
            .isEqualTo(KnowledgeQaService.NO_EVIDENCE_RESPONSE);
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void rejectsMoreThanFiveBasesAndDocumentsOutsideTheScope() {
        Fixture fixture = new Fixture();
        assertThatThrownBy(() -> fixture.service.prepareScope(
            List.of("a", "b", "c", "d", "e", "f"), null, 7L))
            .isInstanceOf(IllegalArgumentException.class);

        when(fixture.bases.requireActive("research"))
            .thenReturn(base(12L, "research"));
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(7L);
        document.setKnowledgeBaseId(99L);
        when(fixture.documents.selectById(7L)).thenReturn(document);
        assertThatThrownBy(() -> fixture.service.prepareScope(
            List.of("research"), 7L, 7L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不属于");
    }

    private static KnowledgeBase base(Long id, String code) {
        KnowledgeBase base = new KnowledgeBase();
        base.setId(id);
        base.setCode(code);
        base.setStatus("ACTIVE");
        return base;
    }

    private static final class Fixture {
        private final KnowledgeChatClient client =
            mock(KnowledgeChatClient.class);
        private final KnowledgeBaseService bases =
            mock(KnowledgeBaseService.class);
        private final PermissionDecisionService decisions =
            mock(PermissionDecisionService.class);
        private final KnowledgeDocumentMapper documents =
            mock(KnowledgeDocumentMapper.class);
        private final RagProperties properties = new RagProperties();
        private final KnowledgeQaService service = new KnowledgeQaService(
            client,
            new CitationAssembler(),
            properties,
            bases,
            decisions,
            documents
        );
    }
}
