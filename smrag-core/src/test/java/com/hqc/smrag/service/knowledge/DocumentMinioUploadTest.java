package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.entity.KnowledgeBase;
import com.hqc.smrag.mapper.KnowledgeChunkMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.service.DocPermissionService;
import com.hqc.smrag.service.storage.DocumentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentMinioUploadTest {

    @TempDir
    Path uploadDir;

    private final KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
    private final KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
    private final DocumentUploadPersistenceService persistence =
        mock(DocumentUploadPersistenceService.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final DocPermissionService permissionService = mock(DocPermissionService.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);

    @org.junit.jupiter.api.BeforeEach
    void configureKnowledgeBase() {
        KnowledgeBase base = new KnowledgeBase();
        base.setId(1L);
        base.setCode("research");
        base.setStatus("ACTIVE");
        when(knowledgeBaseService.requireActive(any())).thenReturn(base);
    }

    @Test
    void rejectsEmptyRolesBeforeCreatingPlaceholderOrWritingObject() {
        DocumentService service = service();

        assertThatThrownBy(() -> service.uploadDocument(file(), "default", List.of()))
            .isInstanceOf(IllegalArgumentException.class);

        verify(persistence, never()).createPlaceholder(any());
        verify(storage, never()).put(any(), any(), anyLong(), any());
    }

    @Test
    void writesObjectKeyUnderDocumentIdThenCompletesDatabaseTransaction() throws Exception {
        when(persistence.createPlaceholder(any())).thenAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(42L);
            return 42L;
        });

        service().uploadDocument(file(), "default", List.of("ADMIN"));

        var key = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(), eq(7L), eq("application/pdf"));
        assertThat(key.getValue()).matches("rag/document/42/[0-9a-f-]+-document.pdf");
        verify(persistence).completeUpload(
            eq(42L), eq(key.getValue()),
            eq("ed7002b439e9ac845f22357d822bac1444730fbdb6016d3ec9432297b9ec9f73"),
            eq(List.of("ADMIN")));
    }

    @Test
    void completionFailureDeletesUploadedObjectAndPlaceholder() throws Exception {
        when(persistence.createPlaceholder(any())).thenAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(42L);
            return 42L;
        });
        doThrow(new IllegalStateException("transaction failed"))
            .when(persistence).completeUpload(eq(42L), any(), any(), eq(List.of("ADMIN")));

        assertThatThrownBy(() ->
            service().uploadDocument(file(), "default", List.of("ADMIN")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("transaction failed");

        var key = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storage).delete(key.capture());
        verify(persistence).deletePlaceholder(42L);
        assertThat(key.getValue()).startsWith("rag/document/42/");
    }

    private DocumentService service() {
        return new DocumentService(
            documentMapper, chunkMapper, persistence, vectorStore, new RagProperties(),
            uploadDir.toString(), permissionService, storage, "rag/document",
            knowledgeBaseService);
    }

    private MockMultipartFile file() {
        return new MockMultipartFile(
            "file", "document.pdf", "application/pdf",
            "content".getBytes(StandardCharsets.UTF_8));
    }
}
