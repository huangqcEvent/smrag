package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.service.DocPermissionService;
import com.hqc.smrag.service.storage.DocumentStorage;
import com.hqc.smrag.service.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentFileServiceTest {

    private final KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
    private final DocPermissionService permissions = mock(DocPermissionService.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final DocumentFileService service =
        new DocumentFileService(mapper, permissions, storage);

    @Test
    void returnsMinioFileForAuthorizedUser() {
        KnowledgeDocument document = document();
        when(mapper.selectById(42L)).thenReturn(document);
        when(permissions.getAccessibleDocIds(7L)).thenReturn(List.of(42L));
        when(storage.get(document.getObjectKey(), null, null)).thenReturn(
            new StoredObject(
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)),
                7, 7, "application/pdf"));

        DocumentFileService.DocumentFile file = service.read(42L, 7L);

        assertThat(file.content()).isEqualTo("content".getBytes(StandardCharsets.UTF_8));
        assertThat(file.contentType()).isEqualTo("application/pdf");
        assertThat(file.fileName()).isEqualTo("manual.pdf");
    }

    @Test
    void deniesFileBeforeReadingStorageWhenUserHasNoPermission() {
        when(mapper.selectById(42L)).thenReturn(document());
        when(permissions.getAccessibleDocIds(7L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.read(42L, 7L))
            .isInstanceOf(AccessDeniedException.class);

        verify(storage, never()).get("rag/document/42/manual.pdf", null, null);
    }

    private KnowledgeDocument document() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(42L);
        document.setName("manual.pdf");
        document.setFileType("PDF");
        document.setStorageType("MINIO");
        document.setObjectKey("rag/document/42/manual.pdf");
        return document;
    }
}
