package com.hqc.smrag.service.knowledge;

import cn.hutool.json.JSONUtil;
import com.hqc.smrag.dto.DocumentParseTaskDTO;
import com.hqc.smrag.entity.DocumentIngestionOutbox;
import com.hqc.smrag.mapper.DocumentIngestionOutboxMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.service.DocPermissionService;
import com.hqc.smrag.entity.KnowledgeDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentUploadCompletionTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
            KnowledgeDocument.class);
    }

    @Test
    void storesPermissionsBeforeCreatingOutboxInTransactionalCompletion() throws Exception {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        DocumentIngestionOutboxMapper outboxMapper = mock(DocumentIngestionOutboxMapper.class);
        DocPermissionService permissionService = mock(DocPermissionService.class);
        DocumentUploadPersistenceService service =
            new DocumentUploadPersistenceService(documentMapper, outboxMapper, permissionService);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(42L);
        document.setFileType("PDF");
        document.setKnowledgeBaseId("default");
        document.setVersion(1);
        when(documentMapper.selectById(42L)).thenReturn(document);

        service.completeUpload(
            42L, "rag/document/42/file.pdf", "checksum", List.of("ADMIN"));

        verify(permissionService).setDocumentPermissions(eq(42L), any());
        ArgumentCaptor<DocumentIngestionOutbox> outbox =
            ArgumentCaptor.forClass(DocumentIngestionOutbox.class);
        verify(outboxMapper).insert(outbox.capture());
        DocumentParseTaskDTO task =
            JSONUtil.toBean(outbox.getValue().getPayload(), DocumentParseTaskDTO.class);
        assertThat(task.getObjectKey()).isEqualTo("rag/document/42/file.pdf");
        assertThat(DocumentUploadPersistenceService.class
            .getMethod("completeUpload", Long.class, String.class, String.class, List.class)
            .getAnnotation(Transactional.class)).isNotNull();
    }
}
