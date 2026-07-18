package com.hqc.smrag.service.knowledge;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.entity.KnowledgeChunk;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.enums.DocumentStatus;
import com.hqc.smrag.mapper.KnowledgeChunkMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.service.DocPermissionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ai.vectorstore.VectorStore;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    @TempDir
    Path uploadDir;

    private final KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
    private final KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final DocumentUploadPersistenceService uploadPersistenceService =
        mock(DocumentUploadPersistenceService.class);
    private final RagProperties properties = new RagProperties();
    private final DocPermissionService docPermissionService = mock(DocPermissionService.class);

    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocument.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeChunk.class);
    }

    @Test
    void uploadDocumentRejectsEmptyFile() {
        DocumentService documentService = service();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "empty.pdf",
            "application/pdf",
            new byte[0]
        );

        assertThatThrownBy(() -> documentService.uploadDocument(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("文件不能为空");
    }

    @Test
    void uploadDocumentRejectsUnsupportedFileType() {
        DocumentService documentService = service();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "notes.exe",
            "application/octet-stream",
            "content".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> documentService.uploadDocument(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不支持的文件类型");
    }

    @Test
    void uploadDocumentSanitizesFileNameAndDetectsTypeCaseInsensitively() throws Exception {
        DocumentService documentService = service();
        when(uploadPersistenceService.createDocumentAndOutbox(any(KnowledgeDocument.class)))
            .thenAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(42L);
            return 42L;
        });
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "../销售手册.PDF",
            "application/pdf",
            "content".getBytes(StandardCharsets.UTF_8)
        );

        documentService.uploadDocument(file);

        ArgumentCaptor<KnowledgeDocument> documentCaptor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(uploadPersistenceService).createDocumentAndOutbox(documentCaptor.capture());
        KnowledgeDocument document = documentCaptor.getValue();

        assertThat(document.getName()).isEqualTo("销售手册.PDF");
        assertThat(document.getFilePath()).endsWith("_销售手册.PDF");
        assertThat(document.getFilePath()).doesNotContain("..");
        assertThat(document.getFileType()).isEqualTo("PDF");
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING.name());
        assertThat(document.getKnowledgeBaseId()).isEqualTo("default");
        assertThat(document.getVersion()).isEqualTo(1);
        assertThat(document.getProcessingToken()).isNotNull();
        assertThat(document.getChecksum())
            .isEqualTo("ed7002b439e9ac845f22357d822bac1444730fbdb6016d3ec9432297b9ec9f73");

    }

    @Test
    void uploadDocumentRejectsConfiguredOversizeBeforeWriting() {
        properties.setMaxUploadSizeBytes(3);
        DocumentService documentService = service();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "large.pdf",
            "application/pdf",
            "four".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> documentService.uploadDocument(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("文件大小");
        verify(uploadPersistenceService, never())
            .createDocumentAndOutbox(any(KnowledgeDocument.class));
    }

    @Test
    void uploadDocumentDeletesFileWhenTransactionalPersistenceFails() {
        DocumentService documentService = service();
        doThrow(new IllegalStateException("transaction failed"))
            .when(uploadPersistenceService)
            .createDocumentAndOutbox(any(KnowledgeDocument.class));
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "document.pdf",
            "application/pdf",
            "content".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> documentService.uploadDocument(file))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("transaction failed");
        assertThat(uploadDir).isEmptyDirectory();
    }

    @Test
    void deleteRemovesVectorsBeforeRelationalRecords() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(42L);
        document.setKnowledgeBaseId("default");
        document.setFilePath(uploadDir.resolve("document.pdf").toString());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        KnowledgeChunk firstChunk = chunk(1L, first);
        KnowledgeChunk secondChunk = chunk(2L, second);
        when(documentMapper.selectById(42L)).thenReturn(document);
        when(documentMapper.update(any(), any())).thenReturn(1);
        when(chunkMapper.selectList(any())).thenReturn(List.of(firstChunk, secondChunk));

        service().deleteDocument(42L);

        InOrder order = inOrder(vectorStore, chunkMapper, documentMapper);
        order.verify(vectorStore).delete(List.of(first.toString(), second.toString()));
        order.verify(chunkMapper).delete(any());
        order.verify(documentMapper).deleteById(42L);
    }

    @Test
    void deleteKeepsRelationalRecordsWhenVectorDeletionFails() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(42L);
        document.setKnowledgeBaseId("default");
        document.setFilePath(uploadDir.resolve("document.pdf").toString());
        UUID vectorId = UUID.randomUUID();
        when(documentMapper.selectById(42L)).thenReturn(document);
        when(documentMapper.update(any(), any())).thenReturn(1);
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk(1L, vectorId)));
        doThrow(new IllegalStateException("vector delete failed"))
            .when(vectorStore).delete(List.of(vectorId.toString()));

        assertThatThrownBy(() -> service().deleteDocument(42L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("vector delete failed");
        verify(chunkMapper, never()).delete(any());
        verify(documentMapper, never()).deleteById(42L);
    }

    private DocumentService service() {
        return new DocumentService(
            documentMapper,
            chunkMapper,
            uploadPersistenceService,
            vectorStore,
            properties,
            uploadDir.toString(),
            docPermissionService
        );
    }

    private KnowledgeChunk chunk(long id, UUID vectorId) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setDocumentId(42L);
        chunk.setVectorId(vectorId);
        return chunk;
    }
}
