package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.service.storage.DocumentStorage;
import com.hqc.smrag.service.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentFileServiceTest {

    private final KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
    private final PermissionDecisionService permissions =
        mock(PermissionDecisionService.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final DocumentFileService service =
        new DocumentFileService(mapper, permissions, storage);

    @Test
    void returnsMinioFileAfterRealTimePermissionCheck() {
        KnowledgeDocument document = document();
        when(mapper.selectById(42L)).thenReturn(document);
        when(storage.get(document.getObjectKey(), null, null)).thenReturn(
            new StoredObject(
                new ByteArrayInputStream(
                    "content".getBytes(StandardCharsets.UTF_8)),
                7,
                7,
                "application/pdf"
            )
        );

        DocumentFileService.DocumentFile file = service.read(42L, 7L);

        verify(permissions).requireDocumentRead(7L, 42L);
        assertThat(file.content())
            .isEqualTo("content".getBytes(StandardCharsets.UTF_8));
        assertThat(file.fileName()).isEqualTo("manual.pdf");
    }

    @Test
    void deniesBeforeReadingObjectStorage() {
        when(mapper.selectById(42L)).thenReturn(document());
        doThrow(new AccessDeniedException("没有权限"))
            .when(permissions).requireDocumentRead(7L, 42L);

        assertThatThrownBy(() -> service.read(42L, 7L))
            .isInstanceOf(AccessDeniedException.class);
        verify(storage, never()).get(
            "rag/document/42/manual.pdf", null, null);
    }

    private KnowledgeDocument document() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(42L);
        document.setName("manual.pdf");
        document.setFileType("PDF");
        document.setStorageType("MINIO");
        document.setObjectKey("rag/document/42/manual.pdf");
        document.setKnowledgeBaseId(1L);
        return document;
    }
}
