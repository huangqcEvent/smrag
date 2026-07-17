package com.hqc.smrag.service.knowledge;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hqc.smrag.entity.KnowledgeChunk;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.mapper.KnowledgeChunkMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentIngestionPersistenceServiceTest {

    private static final UUID PROCESSING_TOKEN =
        UUID.fromString("00000000-0000-0000-0000-000000000042");

    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocument.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeChunk.class);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void mapsAndBatchInsertsChunksBeforeCompletingDocument() {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        DocumentIngestionPersistenceService service =
            new DocumentIngestionPersistenceService(documentMapper, chunkMapper);
        UUID vectorId = UUID.randomUUID();
        Document chunk = new Document(
            vectorId.toString(),
            "chunk text",
            Map.of("chunkIndex", 4, "pageNumber", 7)
        );
        when(documentMapper.update(any(), any())).thenReturn(1);

        service.saveCompleted(42L, 2, PROCESSING_TOKEN, List.of(chunk));

        ArgumentCaptor<Collection> chunksCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(chunkMapper).insert(chunksCaptor.capture(), eq(500));
        Collection<KnowledgeChunk> persisted = chunksCaptor.getValue();
        assertThat(persisted).singleElement().satisfies(saved -> {
            assertThat(saved.getDocumentId()).isEqualTo(42L);
            assertThat(saved.getDocumentVersion()).isEqualTo(2);
            assertThat(saved.getVectorId()).isEqualTo(vectorId);
            assertThat(saved.getChunkIndex()).isEqualTo(4);
            assertThat(saved.getPageNumber()).isEqualTo(7);
            assertThat(saved.getContent()).isEqualTo("chunk text");
        });
        verify(documentMapper).update(any(), any());
    }

    @Test
    void usesIndependentTransactionForFailureStatus() throws Exception {
        Method saveCompleted = DocumentIngestionPersistenceService.class
            .getMethod("saveCompleted", long.class, int.class, UUID.class, List.class);
        Method markFailed = DocumentIngestionPersistenceService.class
            .getMethod(
                "markFailedInNewTransaction",
                long.class,
                int.class,
                UUID.class,
                String.class
            );

        assertThat(saveCompleted.getAnnotation(Transactional.class).propagation())
            .isEqualTo(Propagation.REQUIRED);
        assertThat(markFailed.getAnnotation(Transactional.class).propagation())
            .isEqualTo(Propagation.REQUIRES_NEW);
    }
}
