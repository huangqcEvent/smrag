package com.hqc.smrag.service.chat;

import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.mapper.AuthorizedVectorMapper;
import com.hqc.smrag.mapper.projection.AuthorizedVectorRow;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizedPgVectorRetrieverTest {

    @Test
    void embedsOnceSearchesEachBaseAndMergesByScore() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        AuthorizedVectorMapper mapper = mock(AuthorizedVectorMapper.class);
        RagProperties properties = new RagProperties();
        properties.setTopK(2);
        properties.setSimilarityThreshold(0.4);
        AuthorizedPgVectorRetriever retriever =
            new AuthorizedPgVectorRetriever(embeddingModel, mapper, properties);
        float[] embedding = {0.1f, 0.2f};
        when(embeddingModel.embed("问题")).thenReturn(embedding);
        when(mapper.search(12L, 7L, false, null, embedding, 0.4, 2))
            .thenReturn(List.of(row("a", 0.72, 12L)));
        when(mapper.search(18L, 7L, false, null, embedding, 0.4, 2))
            .thenReturn(List.of(
                row("b", 0.91, 18L),
                row("a", 0.70, 18L)
            ));

        List<Document> result = retriever.retrieve(
            "问题",
            new KnowledgeRetrievalScope(7L, List.of(12L, 18L), null, false)
        );

        verify(embeddingModel, times(1)).embed("问题");
        assertThat(result).extracting(Document::getId)
            .containsExactly("b", "a");
        assertThat(result.get(0).getMetadata())
            .containsEntry("knowledgeBaseId", 18L)
            .containsEntry("knowledgeBaseName", "知识库18");
    }

    @Test
    void emptyScopeNeverFallsBackToUnrestrictedRetrieval() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        AuthorizedVectorMapper mapper = mock(AuthorizedVectorMapper.class);
        AuthorizedPgVectorRetriever retriever =
            new AuthorizedPgVectorRetriever(
                embeddingModel, mapper, new RagProperties());

        assertThat(retriever.retrieve(
            "问题",
            new KnowledgeRetrievalScope(7L, List.of(), null, false)
        )).isEmpty();
        verify(embeddingModel, never()).embed("问题");
    }

    private AuthorizedVectorRow row(
        String id,
        double score,
        Long knowledgeBaseId
    ) {
        return new AuthorizedVectorRow(
            id,
            "content-" + id,
            Map.of("documentId", 7L),
            score,
            knowledgeBaseId,
            "base-" + knowledgeBaseId,
            "知识库" + knowledgeBaseId
        );
    }
}
