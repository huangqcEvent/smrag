package com.hqc.smrag.service.knowledge;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hqc.smrag.dto.DocumentParseTaskDTO;
import com.hqc.smrag.dto.SetDocPermissionRequest;
import com.hqc.smrag.entity.DocumentIngestionOutbox;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.entity.KnowledgeBase;
import com.hqc.smrag.mapper.DocumentIngestionOutboxMapper;
import com.hqc.smrag.mapper.KnowledgeBaseMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.service.DocPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentUploadPersistenceService {

    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentIngestionOutboxMapper outboxMapper;
    private final DocPermissionService permissionService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    public DocumentUploadPersistenceService(
        KnowledgeDocumentMapper documentMapper,
        DocumentIngestionOutboxMapper outboxMapper,
        DocPermissionService permissionService,
        KnowledgeBaseMapper knowledgeBaseMapper
    ) {
        this.documentMapper = documentMapper;
        this.outboxMapper = outboxMapper;
        this.permissionService = permissionService;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    @Transactional
    public Long createPlaceholder(KnowledgeDocument document) {
        documentMapper.insert(document);
        return document.getId();
    }

    @Transactional
    public void completeUpload(
        Long documentId,
        String objectKey,
        String checksum,
        List<String> roleCodes
    ) {
        KnowledgeDocument observed = documentMapper.selectById(documentId);
        if (observed == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        KnowledgeBase knowledgeBase =
            knowledgeBaseMapper.selectByIdForUpdate(observed.getKnowledgeBaseId());
        if (knowledgeBase == null
            || !KnowledgeBaseService.ACTIVE.equals(knowledgeBase.getStatus())) {
            throw new IllegalStateException("知识库已停用，上传已取消");
        }
        KnowledgeDocument locked = documentMapper.selectByIdForUpdate(documentId);
        if (locked == null
            || !observed.getKnowledgeBaseId().equals(locked.getKnowledgeBaseId())) {
            throw new IllegalStateException("文档状态已变化，请重新上传");
        }
        documentMapper.update(
            null,
            Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, documentId)
                .set(KnowledgeDocument::getStorageType, "MINIO")
                .set(KnowledgeDocument::getObjectKey, objectKey)
                .set(KnowledgeDocument::getChecksum, checksum)
                .set(KnowledgeDocument::getUpdateTime, LocalDateTime.now())
        );

        SetDocPermissionRequest request = new SetDocPermissionRequest();
        request.setPermissions(roleCodes.stream().map(code -> {
            SetDocPermissionRequest.RolePermission permission =
                new SetDocPermissionRequest.RolePermission();
            permission.setRoleCode(code);
            permission.setCanRead(true);
            return permission;
        }).toList());
        permissionService.setDocumentPermissions(documentId, request);

        KnowledgeDocument document = documentMapper.selectById(documentId);
        DocumentParseTaskDTO task = new DocumentParseTaskDTO(
            documentId,
            document.getFilePath(),
            objectKey,
            document.getFileType(),
            document.getKnowledgeBaseId(),
            document.getVersion(),
            checksum,
            document.getProcessingToken()
        );
        LocalDateTime now = LocalDateTime.now();
        DocumentIngestionOutbox outbox = new DocumentIngestionOutbox();
        outbox.setId(UUID.randomUUID());
        outbox.setDocumentId(documentId);
        outbox.setPayload(JSONUtil.toJsonStr(task));
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(now);
        outbox.setCreatedAt(now);
        outboxMapper.insert(outbox);
    }

    @Transactional
    public void deletePlaceholder(Long documentId) {
        documentMapper.deleteById(documentId);
    }

}
