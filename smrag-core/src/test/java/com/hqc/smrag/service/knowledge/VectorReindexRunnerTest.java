package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.entity.KnowledgeChunk;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.enums.DocumentStatus;
import com.hqc.smrag.mapper.DocPermissionMapper;
import com.hqc.smrag.mapper.KnowledgeChunkMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.service.DocPermissionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorReindexRunnerTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void repeatedRunsUseTheSameStableVectorIds() throws Exception {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        DocPermissionMapper permissionMapper = mock(DocPermissionMapper.class);
        DocPermissionService permissionService = mock(DocPermissionService.class);
        VectorStore vectorStore = mock(VectorStore.class);
        VectorReindexRunner runner = new VectorReindexRunner(
            documentMapper,
            chunkMapper,
            permissionMapper,
            permissionService,
            vectorStore,
            new VectorDocumentIdGenerator()
        );
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(42L);
        document.setKnowledgeBaseId("default");
        document.setName("legacy.pdf");
        document.setFileType("PDF");
        document.setVersion(1);
        document.setAclVersion(3);
        document.setChecksum("legacy-checksum");
        document.setStatus(DocumentStatus.COMPLETED.name());
        KnowledgeChunk first = chunk(1L, 0, "first");
        KnowledgeChunk second = chunk(2L, 1, "second");
        when(documentMapper.selectList(any())).thenReturn(List.of(document));
        when(chunkMapper.selectList(any())).thenReturn(List.of(first, second));
        when(permissionMapper.selectReadableRoleCodesByDocumentId(42L))
            .thenReturn(List.of("ADMIN", "AUDITOR"));

        runner.run(new DefaultApplicationArguments());
        runner.run(new DefaultApplicationArguments());

        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).add(documentsCaptor.capture());
        verify(permissionService, times(2)).synchronizeVectorAcl(42L);
        assertThat(documentsCaptor.getAllValues().get(0)).extracting(Document::getId)
            .containsExactlyElementsOf(
                documentsCaptor.getAllValues().get(1).stream().map(Document::getId).toList()
            );
        assertThat(documentsCaptor.getAllValues().get(0))
            .allSatisfy(vector -> assertThat(vector.getMetadata())
                .containsEntry("aclVersion", 3)
                .containsEntry("aclDenied", false)
                .containsEntry("aclRole_ADMIN", true)
                .containsEntry("aclRole_AUDITOR", true)
                .doesNotContainKey("aclRole_USER"));
        ArgumentCaptor<Collection> chunksCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(chunkMapper, times(2)).updateById(chunksCaptor.capture(), eq(500));
        assertThat(first.getVectorId()).isNotNull();
        assertThat(second.getVectorId()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deletesNewVectorsWhenChunkBackfillFails() throws Exception {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        DocPermissionMapper permissionMapper = mock(DocPermissionMapper.class);
        DocPermissionService permissionService = mock(DocPermissionService.class);
        VectorStore vectorStore = mock(VectorStore.class);
        VectorReindexRunner runner = new VectorReindexRunner(
            documentMapper,
            chunkMapper,
            permissionMapper,
            permissionService,
            vectorStore,
            new VectorDocumentIdGenerator()
        );
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(42L);
        document.setKnowledgeBaseId("default");
        document.setName("legacy.pdf");
        document.setFileType("PDF");
        document.setVersion(2);
        document.setStatus(DocumentStatus.COMPLETED.name());
        KnowledgeChunk current = chunk(2L, 0, "current");
        current.setDocumentVersion(2);
        when(documentMapper.selectList(any())).thenReturn(List.of(document));
        when(chunkMapper.selectList(any())).thenReturn(List.of(current));
        when(permissionMapper.selectReadableRoleCodesByDocumentId(42L))
            .thenReturn(List.of("ADMIN"));
        doThrow(new IllegalStateException("backfill failed"))
            .when(chunkMapper).updateById(any(Collection.class), eq(500));

        runner.run(new DefaultApplicationArguments());

        String expectedId = new VectorDocumentIdGenerator()
            .generate("default", 42L, 2, 0)
            .toString();
        verify(vectorStore).delete(List.of(expectedId));
    }

    private KnowledgeChunk chunk(long id, int index, String content) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setDocumentId(42L);
        chunk.setDocumentVersion(1);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        return chunk;
    }
}
