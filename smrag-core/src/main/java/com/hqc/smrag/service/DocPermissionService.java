package com.hqc.smrag.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hqc.smrag.dto.SetDocPermissionRequest;
import com.hqc.smrag.entity.DocPermission;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.entity.SysRole;
import com.hqc.smrag.mapper.DocPermissionMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.mapper.SysRoleMapper;
import com.hqc.smrag.mapper.VectorAclMapper;
import com.hqc.smrag.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocPermissionService {
    private final DocPermissionMapper docPermissionMapper;
    private final SysRoleMapper sysRoleMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final VectorAclMapper vectorAclMapper;

    @Transactional
    public void setDocumentPermissions(Long documentId, SetDocPermissionRequest request) {
        LinkedHashSet<String> codes = request.getPermissions().stream()
            .filter(permission -> Boolean.TRUE.equals(permission.getCanRead()))
            .map(SetDocPermissionRequest.RolePermission::getRoleCode)
            .map(String::trim).map(String::toUpperCase)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("文档至少需要一个可读角色");
        }
        List<SysRole> roles = sysRoleMapper.selectByCodesForKeyShare(codes);
        Map<String, SysRole> rolesByCode = roles.stream().collect(Collectors.toMap(
            SysRole::getRoleCode, Function.identity()));
        codes.stream().filter(code -> !rolesByCode.containsKey(code)).findFirst()
            .ifPresent(code -> { throw new IllegalArgumentException("角色不存在: " + code); });

        KnowledgeDocument document = lockDocument(documentId);
        docPermissionMapper.delete(new LambdaQueryWrapper<DocPermission>()
            .eq(DocPermission::getDocumentId, documentId));
        for (String code : codes) insertPermission(documentId, rolesByCode.get(code));
        int aclVersion = Objects.requireNonNullElse(document.getAclVersion(), 0) + 1;
        documentMapper.update(
            null,
            Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, documentId)
                .set(KnowledgeDocument::getAclVersion, aclVersion)
                .set(KnowledgeDocument::getUpdateTime, LocalDateTime.now()));
        updateVectorAcl(documentId, aclVersion, codes);
    }

    @Transactional
    public void setDefaultPermissions(Long documentId) {
        SetDocPermissionRequest request = new SetDocPermissionRequest();
        request.setPermissions(List.of("ADMIN", "USER").stream().map(code -> {
            SetDocPermissionRequest.RolePermission permission =
                new SetDocPermissionRequest.RolePermission();
            permission.setRoleCode(code);
            return permission;
        }).toList());
        setDocumentPermissions(documentId, request);
    }

    public List<Long> getAccessibleDocIds(Long userId) {
        return docPermissionMapper.selectAccessibleDocIdsByUserId(userId);
    }

    @Transactional
    public void synchronizeVectorAcl(Long documentId) {
        KnowledgeDocument document = lockDocument(documentId);
        LinkedHashSet<String> codes = new LinkedHashSet<>(
            docPermissionMapper.selectReadableRoleCodesByDocumentId(documentId)
        );
        if (codes.isEmpty()) {
            throw new IllegalStateException(
                "Document " + documentId + " has no readable roles"
            );
        }
        updateVectorAcl(
            documentId,
            Objects.requireNonNullElse(document.getAclVersion(), 1),
            codes
        );
    }

    public Map<Long, List<RoleVO>> getReadableRolesByDocumentIds(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) return Map.of();
        Map<Long, List<RoleVO>> result = new LinkedHashMap<>();
        docPermissionMapper.selectReadableRolesByDocumentIds(documentIds).forEach(row ->
            result.computeIfAbsent(row.getDocumentId(), ignored -> new ArrayList<>())
                .add(new RoleVO(row.getRoleId(), row.getRoleCode(), row.getRoleName(),
                    row.getDescription())));
        return result;
    }

    private void insertPermission(Long documentId, SysRole role) {
        DocPermission permission = new DocPermission();
        permission.setDocumentId(documentId);
        permission.setRoleId(role.getId());
        permission.setCanRead(true);
        permission.setCreatedAt(LocalDateTime.now());
        permission.setUpdatedAt(LocalDateTime.now());
        docPermissionMapper.insert(permission);
    }

    private KnowledgeDocument lockDocument(Long documentId) {
        KnowledgeDocument document = documentMapper.selectByIdForUpdate(documentId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        return document;
    }

    private void updateVectorAcl(
        Long documentId,
        int aclVersion,
        Set<String> codes
    ) {
        Map<String, Object> acl = new LinkedHashMap<>();
        acl.put("aclVersion", aclVersion);
        acl.put("aclDenied", false);
        codes.forEach(code -> acl.put("aclRole_" + code, true));
        vectorAclMapper.updateDocumentAcl(documentId, JSONUtil.toJsonStr(acl));
    }
}
