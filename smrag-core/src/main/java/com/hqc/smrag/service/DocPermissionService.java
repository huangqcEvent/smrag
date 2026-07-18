package com.hqc.smrag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hqc.smrag.dto.SetDocPermissionRequest;
import com.hqc.smrag.entity.DocPermission;
import com.hqc.smrag.entity.SysRole;
import com.hqc.smrag.mapper.DocPermissionMapper;
import com.hqc.smrag.mapper.SysRoleMapper;
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

    @Transactional
    public void setDocumentPermissions(Long documentId, SetDocPermissionRequest request) {
        LinkedHashSet<String> codes = request.getPermissions().stream()
            .map(SetDocPermissionRequest.RolePermission::getRoleCode)
            .map(String::trim).map(String::toUpperCase)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<SysRole> roles = codes.isEmpty() ? List.of() : sysRoleMapper.selectList(
            new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleCode, codes));
        Map<String, SysRole> rolesByCode = roles.stream().collect(Collectors.toMap(
            SysRole::getRoleCode, Function.identity()));
        codes.stream().filter(code -> !rolesByCode.containsKey(code)).findFirst()
            .ifPresent(code -> { throw new IllegalArgumentException("角色不存在: " + code); });

        docPermissionMapper.delete(new LambdaQueryWrapper<DocPermission>()
            .eq(DocPermission::getDocumentId, documentId));
        for (String code : codes) insertPermission(documentId, rolesByCode.get(code));
    }

    @Transactional
    public void setDefaultPermissions(Long documentId) {
        sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getRoleCode, "ADMIN", "USER"))
            .forEach(role -> insertPermission(documentId, role));
    }

    public List<Long> getAccessibleDocIds(Long userId) {
        return docPermissionMapper.selectAccessibleDocIdsByUserId(userId);
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
}
