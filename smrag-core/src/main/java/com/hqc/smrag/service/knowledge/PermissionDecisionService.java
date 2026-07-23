package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.entity.KnowledgeBase;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.mapper.DocPermissionMapper;
import com.hqc.smrag.mapper.KnowledgeBaseMapper;
import com.hqc.smrag.mapper.KnowledgeBaseRoleMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.mapper.SysUserRoleMapper;
import com.hqc.smrag.exception.DomainConflictException;
import com.hqc.smrag.exception.DomainForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionDecisionService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseRoleMapper knowledgeBaseRoleMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final DocPermissionMapper documentPermissionMapper;
    private final SysUserRoleMapper userRoleMapper;

    public boolean isGlobalAdmin(Long userId) {
        return userId != null && userRoleMapper.userHasRole(userId, "ADMIN");
    }

    public boolean canAccessKnowledgeBase(Long userId, Long knowledgeBaseId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (knowledgeBase == null
            || !KnowledgeBaseService.ACTIVE.equals(knowledgeBase.getStatus())) {
            return false;
        }
        List<Long> baseRoles =
            knowledgeBaseRoleMapper.selectRoleIds(knowledgeBaseId);
        if (baseRoles.isEmpty()) {
            return false;
        }
        if (isGlobalAdmin(userId)) {
            return true;
        }
        return intersects(
            documentPermissionMapper.selectRoleIdsByUserId(userId),
            baseRoles
        );
    }

    public void requireKnowledgeBaseAccess(Long userId, List<Long> knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            throw new IllegalArgumentException("至少选择一个知识库");
        }
        for (Long knowledgeBaseId : new LinkedHashSet<>(knowledgeBaseIds)) {
            if (!canAccessKnowledgeBase(userId, knowledgeBaseId)) {
                throw new DomainForbiddenException(
                    "KNOWLEDGE_BASE_FORBIDDEN",
                    "没有权限访问知识库: " + knowledgeBaseId,
                    java.util.Map.of("knowledgeBaseId", knowledgeBaseId)
                );
            }
        }
    }

    public boolean canReadDocument(Long userId, Long documentId) {
        KnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null || userId == null) {
            return false;
        }
        KnowledgeBase knowledgeBase =
            knowledgeBaseMapper.selectById(document.getKnowledgeBaseId());
        if (knowledgeBase == null) {
            return false;
        }
        List<Long> baseRoles =
            knowledgeBaseRoleMapper.selectRoleIds(document.getKnowledgeBaseId());
        List<Long> documentRoles =
            documentPermissionMapper.selectReadableRoleIdsByDocumentId(documentId);
        if (baseRoles.isEmpty() || documentRoles.isEmpty()) {
            return false;
        }
        if (isGlobalAdmin(userId)) {
            return true;
        }
        if (!KnowledgeBaseService.ACTIVE.equals(knowledgeBase.getStatus())) {
            return false;
        }
        List<Long> userRoles = documentPermissionMapper.selectRoleIdsByUserId(userId);
        return intersects(userRoles, baseRoles)
            && intersects(userRoles, documentRoles);
    }

    public void requireDocumentRead(Long userId, Long documentId) {
        if (!canReadDocument(userId, documentId)) {
            throw new DomainForbiddenException(
                "DOCUMENT_FORBIDDEN",
                "没有权限查看该文档",
                java.util.Map.of("documentId", documentId)
            );
        }
    }

    public void validateDocumentRoles(Long knowledgeBaseId, List<Long> roleIds) {
        Set<Long> requested = roleIds == null
            ? Set.of()
            : roleIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("文档至少需要一个可读角色");
        }
        Set<Long> allowed = new LinkedHashSet<>(
            knowledgeBaseRoleMapper.selectRoleIds(knowledgeBaseId)
        );
        Set<Long> outside = new LinkedHashSet<>(requested);
        outside.removeAll(allowed);
        if (!outside.isEmpty()) {
            throw new DomainConflictException(
                "DOCUMENT_ROLE_OUTSIDE_BASE",
                "文档角色不属于知识库授权范围: " + outside,
                java.util.Map.of("roleIds", outside)
            );
        }
    }

    private boolean intersects(List<Long> left, List<Long> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        Set<Long> values = new java.util.HashSet<>(left);
        return right.stream().anyMatch(values::contains);
    }
}
