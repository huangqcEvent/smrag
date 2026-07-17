package com.hqc.smrag.service.knowledge;

import cn.hutool.json.JSONUtil;
import com.hqc.smrag.dto.DocumentParseTaskDTO;
import com.hqc.smrag.entity.DocumentIngestionOutbox;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.mapper.DocumentIngestionOutboxMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentUploadPersistenceServiceTest {

    @Test
    void storesDocumentAndOutboxInOneTransaction() throws Exception {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        DocumentIngestionOutboxMapper outboxMapper = mock(DocumentIngestionOutboxMapper.class);
        DocumentUploadPersistenceService service =
            new DocumentUploadPersistenceService(documentMapper, outboxMapper);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setKnowledgeBaseId("default");
        document.setFilePath("uploads/document.pdf");
        document.setFileType("PDF");
        document.setVersion(1);
        document.setChecksum("checksum");
        document.setProcessingToken(UUID.randomUUID());
        when(documentMapper.insert(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument saved = invocation.getArgument(0);
            saved.setId(42L);
            return 1;
        });

        assertThat(service.createDocumentAndOutbox(document)).isEqualTo(42L);

        ArgumentCaptor<DocumentIngestionOutbox> captor =
            ArgumentCaptor.forClass(DocumentIngestionOutbox.class);
        verify(outboxMapper).insert(captor.capture());
        DocumentIngestionOutbox outbox = captor.getValue();
        DocumentParseTaskDTO task = JSONUtil.toBean(outbox.getPayload(), DocumentParseTaskDTO.class);
        assertThat(outbox.getStatus()).isEqualTo("PENDING");
        assertThat(task.getDocumentId()).isEqualTo(42L);
        assertThat(task.getProcessingToken()).isEqualTo(document.getProcessingToken());
        assertThat(DocumentUploadPersistenceService.class
            .getMethod("createDocumentAndOutbox", KnowledgeDocument.class)
            .getAnnotation(Transactional.class)).isNotNull();
    }
}
