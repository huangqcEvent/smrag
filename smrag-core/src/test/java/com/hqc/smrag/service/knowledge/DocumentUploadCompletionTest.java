package com.hqc.smrag.service.knowledge;

import cn.hutool.json.JSONUtil;
import com.hqc.smrag.dto.DocumentParseTaskDTO;
import com.hqc.smrag.entity.DocumentIngestionOutbox;
import com.hqc.smrag.mapper.DocumentIngestionOutboxMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.mapper.KnowledgeBaseMapper;
import com.hqc.smrag.service.DocPermissionService;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.entity.KnowledgeBase;
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
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        DocumentUploadPersistenceService service =
            new DocumentUploadPersistenceService(
                documentMapper, outboxMapper, permissionService, knowledgeBaseMapper);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(42L);
        document.setFileType("PDF");
        document.setKnowledgeBaseId(1L);
        document.setVersion(1);
        document.setChunkingStrategy("RECURSIVE");
        when(documentMapper.selectById(42L)).thenReturn(document);
        when(documentMapper.selectByIdForUpdate(42L)).thenReturn(document);
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(1L);
        knowledgeBase.setStatus(KnowledgeBaseService.ACTIVE);
        when(knowledgeBaseMapper.selectByIdForUpdate(1L)).thenReturn(knowledgeBase);

        service.completeUpload(
            42L, "rag/document/42/file.pdf", "checksum", List.of("ADMIN"));

        verify(permissionService).setDocumentPermissions(eq(42L), any());
        ArgumentCaptor<DocumentIngestionOutbox> outbox =
            ArgumentCaptor.forClass(DocumentIngestionOutbox.class);
        verify(outboxMapper).insert(outbox.capture());
        DocumentParseTaskDTO task =
            JSONUtil.toBean(outbox.getValue().getPayload(), DocumentParseTaskDTO.class);
        assertThat(task.getObjectKey()).isEqualTo("rag/document/42/file.pdf");
        assertThat(outbox.getValue().getPayload())
            .doesNotContain("chunkingStrategy");
        assertThat(DocumentUploadPersistenceService.class
            .getMethod("completeUpload", Long.class, String.class, String.class, List.class)
            .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void cancelsCompletionWhenTheKnowledgeBaseWasDisabledDuringUpload() {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        DocumentIngestionOutboxMapper outboxMapper =
            mock(DocumentIngestionOutboxMapper.class);
        DocPermissionService permissionService = mock(DocPermissionService.class);
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        DocumentUploadPersistenceService service =
            new DocumentUploadPersistenceService(
                documentMapper, outboxMapper, permissionService, knowledgeBaseMapper);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(42L);
        document.setKnowledgeBaseId(1L);
        when(documentMapper.selectById(42L)).thenReturn(document);
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(1L);
        knowledgeBase.setStatus(KnowledgeBaseService.DISABLED);
        when(knowledgeBaseMapper.selectByIdForUpdate(1L)).thenReturn(knowledgeBase);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            service.completeUpload(
                42L, "rag/document/42/file.pdf", "checksum", List.of("ADMIN")
            ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("停用");

        org.mockito.Mockito.verify(outboxMapper, org.mockito.Mockito.never())
            .insert(any(DocumentIngestionOutbox.class));
    }
}
