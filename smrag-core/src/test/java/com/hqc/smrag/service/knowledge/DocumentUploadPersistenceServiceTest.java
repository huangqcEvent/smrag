package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.mapper.DocumentIngestionOutboxMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.mapper.KnowledgeBaseMapper;
import com.hqc.smrag.service.DocPermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentUploadPersistenceServiceTest {

    @Test
    void createsPlaceholderInItsOwnTransaction() throws Exception {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        DocumentUploadPersistenceService service = new DocumentUploadPersistenceService(
            documentMapper,
            mock(DocumentIngestionOutboxMapper.class),
            mock(DocPermissionService.class),
            mock(KnowledgeBaseMapper.class));
        KnowledgeDocument document = new KnowledgeDocument();
        when(documentMapper.insert(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            ((KnowledgeDocument) invocation.getArgument(0)).setId(42L);
            return 1;
        });

        assertThat(service.createPlaceholder(document)).isEqualTo(42L);

        verify(documentMapper).insert(document);
        assertThat(DocumentUploadPersistenceService.class
            .getMethod("createPlaceholder", KnowledgeDocument.class)
            .getAnnotation(Transactional.class)).isNotNull();
    }
}
