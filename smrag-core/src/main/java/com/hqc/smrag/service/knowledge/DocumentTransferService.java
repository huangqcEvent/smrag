package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.dto.MoveDocumentRequest;
import com.hqc.smrag.entity.KnowledgeBase;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.enums.DocumentStatus;
import com.hqc.smrag.exception.DomainConflictException;
import com.hqc.smrag.exception.DomainNotFoundException;
import com.hqc.smrag.mapper.DocPermissionMapper;
import com.hqc.smrag.mapper.KnowledgeBaseMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.mapper.VectorAclMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentTransferService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocPermissionMapper permissionMapper;
    private final VectorAclMapper vectorMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final PermissionDecisionService permissionDecisionService;

    @Transactional
    public void move(Long documentId, MoveDocumentRequest request) {
        KnowledgeDocument observed = documentMapper.selectById(documentId);
        if (observed == null) {
            throw new DomainNotFoundException(
                "DOCUMENT_NOT_FOUND", "文档不存在",
                java.util.Map.of("documentId", documentId)
            );
        }
        KnowledgeBase target =
            knowledgeBaseService.requireActive(request.targetKnowledgeBaseCode());

        long firstId = Math.min(observed.getKnowledgeBaseId(), target.getId());
        long secondId = Math.max(observed.getKnowledgeBaseId(), target.getId());
        KnowledgeBase first = knowledgeBaseMapper.selectByIdForUpdate(firstId);
        KnowledgeBase second = firstId == secondId
            ? first
            : knowledgeBaseMapper.selectByIdForUpdate(secondId);
        if (first == null || second == null) {
            throw new IllegalStateException("文档所属知识库已不存在");
        }
        KnowledgeDocument document = documentMapper.selectByIdForUpdate(documentId);
        if (document == null) {
            throw new DomainNotFoundException(
                "DOCUMENT_NOT_FOUND", "文档不存在",
                java.util.Map.of("documentId", documentId)
            );
        }
        if (!observed.getKnowledgeBaseId().equals(document.getKnowledgeBaseId())) {
            throw new IllegalStateException("文档已被迁移，请刷新后重试");
        }
        if (DocumentStatus.PENDING.name().equals(document.getStatus())
            || DocumentStatus.PROCESSING.name().equals(document.getStatus())
            || DocumentStatus.DELETING.name().equals(document.getStatus())) {
            throw new DomainConflictException(
                "DOCUMENT_PROCESSING",
                "文档正在处理，暂时不能迁移",
                java.util.Map.of(
                    "documentId", documentId,
                    "status", document.getStatus()
                )
            );
        }
        if (target.getId().equals(document.getKnowledgeBaseId())) {
            return;
        }
        KnowledgeBase lockedTarget = target.getId().equals(first.getId()) ? first : second;
        if (!KnowledgeBaseService.ACTIVE.equals(lockedTarget.getStatus())) {
            throw new IllegalStateException("目标知识库已停用，不能迁移文档");
        }
        List<Long> documentRoles =
            permissionMapper.selectReadableRoleIdsByDocumentId(documentId);
        permissionDecisionService.validateDocumentRoles(target.getId(), documentRoles);

        document.setKnowledgeBaseId(target.getId());
        document.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(document);
        vectorMapper.updateKnowledgeBase(documentId, target.getId());
    }
}
