package com.hqc.smrag.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hqc.smrag.dto.CreateKnowledgeBaseRequest;
import com.hqc.smrag.dto.SetKnowledgeBaseRolesRequest;
import com.hqc.smrag.dto.SetKnowledgeBaseStatusRequest;
import com.hqc.smrag.dto.UpdateKnowledgeBaseRequest;
import com.hqc.smrag.entity.KnowledgeBase;
import com.hqc.smrag.entity.KnowledgeBaseRole;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.entity.SysRole;
import com.hqc.smrag.exception.DomainConflictException;
import com.hqc.smrag.exception.DomainNotFoundException;
import com.hqc.smrag.mapper.DocPermissionMapper;
import com.hqc.smrag.mapper.KnowledgeBaseMapper;
import com.hqc.smrag.mapper.KnowledgeBaseRoleMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.mapper.SysRoleMapper;
import com.hqc.smrag.mapper.SysUserRoleMapper;
import com.hqc.smrag.vo.KnowledgeBaseAdminVO;
import com.hqc.smrag.vo.KnowledgeBaseVO;
import com.hqc.smrag.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    public static final String ACTIVE = "ACTIVE";
    public static final String DISABLED = "DISABLED";

    private final KnowledgeBaseMapper mapper;
    private final KnowledgeBaseRoleMapper baseRoleMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final DocPermissionMapper docPermissionMapper;

    public List<KnowledgeBaseVO> listAccessible(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<KnowledgeBase> bases = isGlobalAdmin(userId)
            ? mapper.selectActiveWithRoles()
            : mapper.selectAccessibleByUserId(userId);
        return bases.stream().map(this::toView).toList();
    }

    public List<KnowledgeBaseAdminVO> listAdmin() {
        return mapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .orderByAsc(KnowledgeBase::getCreatedAt)
                .orderByAsc(KnowledgeBase::getId))
            .stream()
            .map(this::toAdminView)
            .toList();
    }

    @Transactional
    public Long create(CreateKnowledgeBaseRequest request, Long creatorId) {
        String code = normalizeCode(request.code());
        if (mapper.selectCount(new LambdaQueryWrapper<KnowledgeBase>()
            .eq(KnowledgeBase::getCode, code)) > 0) {
            throw new DomainConflictException(
                "KNOWLEDGE_BASE_CODE_CONFLICT",
                "知识库编码已存在: " + code,
                java.util.Map.of("code", code)
            );
        }
        LinkedHashSet<Long> roleIds = normalizeRoleIds(request.roleIds());
        validateRolesExist(roleIds);
        LocalDateTime now = LocalDateTime.now();
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setCode(code);
        knowledgeBase.setName(requireName(request.name()));
        knowledgeBase.setDescription(trimToNull(request.description()));
        knowledgeBase.setStatus(ACTIVE);
        knowledgeBase.setVersion(1);
        knowledgeBase.setCreatedBy(requireUserId(creatorId));
        knowledgeBase.setCreatedAt(now);
        knowledgeBase.setUpdatedAt(now);
        mapper.insert(knowledgeBase);
        insertRoles(knowledgeBase.getId(), roleIds, creatorId, now);
        return knowledgeBase.getId();
    }

    @Transactional
    public void update(Long id, UpdateKnowledgeBaseRequest request, Long operatorId) {
        requireUserId(operatorId);
        int updated = mapper.update(
            null,
            Wrappers.<KnowledgeBase>lambdaUpdate()
                .eq(KnowledgeBase::getId, id)
                .eq(KnowledgeBase::getVersion, request.version())
                .set(KnowledgeBase::getName, requireName(request.name()))
                .set(KnowledgeBase::getDescription, trimToNull(request.description()))
                .set(KnowledgeBase::getVersion, request.version() + 1)
                .set(KnowledgeBase::getUpdatedAt, LocalDateTime.now())
        );
        if (updated != 1) {
            throw versionOrMissing(id);
        }
    }

    @Transactional
    public void replaceRoles(
        Long id,
        SetKnowledgeBaseRolesRequest request,
        Long operatorId
    ) {
        KnowledgeBase knowledgeBase = lockAndCheckVersion(id, request.version());
        LinkedHashSet<Long> roleIds = normalizeRoleIds(request.roleIds());
        validateRolesExist(roleIds);
        Set<Long> usedRoles = new LinkedHashSet<>(
            docPermissionMapper.selectReadableRoleIdsByKnowledgeBaseId(id)
        );
        usedRoles.removeAll(roleIds);
        if (!usedRoles.isEmpty()) {
            throw new DomainConflictException(
                "ROLE_STILL_IN_USE",
                "待移除角色仍被库内文档权限使用: " + usedRoles,
                java.util.Map.of("roleIds", usedRoles)
            );
        }
        baseRoleMapper.deleteByKnowledgeBaseId(id);
        LocalDateTime now = LocalDateTime.now();
        insertRoles(id, roleIds, requireUserId(operatorId), now);
        bumpVersion(knowledgeBase, now);
    }

    @Transactional
    public void changeStatus(
        Long id,
        SetKnowledgeBaseStatusRequest request,
        Long operatorId
    ) {
        requireUserId(operatorId);
        KnowledgeBase knowledgeBase = lockAndCheckVersion(id, request.version());
        String status = request.status().trim().toUpperCase(Locale.ROOT);
        if (!ACTIVE.equals(status) && !DISABLED.equals(status)) {
            throw new IllegalArgumentException("知识库状态不合法");
        }
        if (ACTIVE.equals(status) && baseRoleMapper.selectRoleIds(id).isEmpty()) {
            throw new IllegalStateException("启用知识库前至少配置一个角色");
        }
        knowledgeBase.setStatus(status);
        bumpVersion(knowledgeBase, LocalDateTime.now());
    }

    @Transactional
    public void delete(Long id, Long operatorId) {
        requireUserId(operatorId);
        KnowledgeBase knowledgeBase = mapper.selectByIdForUpdate(id);
        if (knowledgeBase == null) {
            throw knowledgeBaseNotFound(id);
        }
        long documentCount = documentMapper.selectCount(
            Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getKnowledgeBaseId, id)
        );
        if (documentCount > 0) {
            throw new DomainConflictException(
                "KNOWLEDGE_BASE_NOT_EMPTY",
                "知识库中仍有 " + documentCount + " 份文档，不能删除",
                java.util.Map.of("documentCount", documentCount)
            );
        }
        mapper.deleteById(id);
    }

    public KnowledgeBase requireByCode(String value) {
        String code = normalizeCode(value);
        KnowledgeBase knowledgeBase = mapper.selectOne(
            new LambdaQueryWrapper<KnowledgeBase>().eq(KnowledgeBase::getCode, code)
        );
        if (knowledgeBase == null) {
            throw new DomainNotFoundException(
                "KNOWLEDGE_BASE_NOT_FOUND",
                "知识库不存在: " + code,
                java.util.Map.of("code", code)
            );
        }
        return knowledgeBase;
    }

    public KnowledgeBase requireActive(String value) {
        KnowledgeBase knowledgeBase = requireByCode(value);
        requireActiveState(knowledgeBase);
        return knowledgeBase;
    }

    public KnowledgeBase requireActiveById(Long id) {
        KnowledgeBase knowledgeBase = id == null ? null : mapper.selectById(id);
        if (knowledgeBase == null) {
            throw knowledgeBaseNotFound(id);
        }
        requireActiveState(knowledgeBase);
        return knowledgeBase;
    }

    public String requireActiveCode(String value) {
        return requireActive(value).getCode();
    }

    public boolean isGlobalAdmin(Long userId) {
        return userId != null && userRoleMapper.userHasRole(userId, "ADMIN");
    }

    public List<Long> roleIds(Long knowledgeBaseId) {
        return baseRoleMapper.selectRoleIds(knowledgeBaseId);
    }

    private void requireActiveState(KnowledgeBase knowledgeBase) {
        if (!ACTIVE.equals(knowledgeBase.getStatus())) {
            throw new DomainConflictException(
                "KNOWLEDGE_BASE_DISABLED",
                "知识库未启用: " + knowledgeBase.getCode(),
                java.util.Map.of("code", knowledgeBase.getCode())
            );
        }
    }

    private KnowledgeBase lockAndCheckVersion(Long id, Integer version) {
        KnowledgeBase knowledgeBase = mapper.selectByIdForUpdate(id);
        if (knowledgeBase == null) {
            throw knowledgeBaseNotFound(id);
        }
        if (!knowledgeBase.getVersion().equals(version)) {
            throw versionConflict(id);
        }
        return knowledgeBase;
    }

    private void bumpVersion(KnowledgeBase knowledgeBase, LocalDateTime now) {
        knowledgeBase.setVersion(knowledgeBase.getVersion() + 1);
        knowledgeBase.setUpdatedAt(now);
        mapper.updateById(knowledgeBase);
    }

    private RuntimeException versionOrMissing(Long id) {
        return mapper.selectById(id) == null
            ? knowledgeBaseNotFound(id)
            : versionConflict(id);
    }

    private DomainNotFoundException knowledgeBaseNotFound(Long id) {
        return new DomainNotFoundException(
            "KNOWLEDGE_BASE_NOT_FOUND",
            "知识库不存在",
            id == null
                ? java.util.Map.of()
                : java.util.Map.of("knowledgeBaseId", id)
        );
    }

    private DomainConflictException versionConflict(Long id) {
        return new DomainConflictException(
            "KNOWLEDGE_BASE_VERSION_CONFLICT",
            "知识库已被其他操作修改，请刷新后重试",
            java.util.Map.of("knowledgeBaseId", id)
        );
    }

    private void validateRolesExist(Set<Long> roleIds) {
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        Set<Long> found = roles.stream().map(SysRole::getId).collect(Collectors.toSet());
        roleIds.stream().filter(id -> !found.contains(id)).findFirst()
            .ifPresent(id -> {
                throw new IllegalArgumentException("角色不存在: " + id);
            });
    }

    private void insertRoles(
        Long knowledgeBaseId,
        Set<Long> roleIds,
        Long creatorId,
        LocalDateTime now
    ) {
        for (Long roleId : roleIds) {
            KnowledgeBaseRole relation = new KnowledgeBaseRole();
            relation.setKnowledgeBaseId(knowledgeBaseId);
            relation.setRoleId(roleId);
            relation.setCreatedBy(creatorId);
            relation.setCreatedAt(now);
            baseRoleMapper.insert(relation);
        }
    }

    private LinkedHashSet<Long> normalizeRoleIds(List<Long> value) {
        LinkedHashSet<Long> ids = value == null
            ? new LinkedHashSet<>()
            : value.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("知识库至少需要一个可访问角色");
        }
        return ids;
    }

    private KnowledgeBaseVO toView(KnowledgeBase knowledgeBase) {
        return new KnowledgeBaseVO(
            knowledgeBase.getId(),
            knowledgeBase.getCode(),
            knowledgeBase.getName(),
            knowledgeBase.getDescription(),
            knowledgeBase.getStatus()
        );
    }

    private KnowledgeBaseAdminVO toAdminView(KnowledgeBase knowledgeBase) {
        List<Long> roleIds = baseRoleMapper.selectRoleIds(knowledgeBase.getId());
        var rolesById = roleMapper.selectBatchIds(roleIds).stream()
            .collect(Collectors.toMap(SysRole::getId, Function.identity()));
        List<RoleVO> roles = roleIds.stream()
            .map(rolesById::get)
            .filter(java.util.Objects::nonNull)
            .map(role -> new RoleVO(
                role.getId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getDescription()
            ))
            .toList();
        long documentCount = documentMapper.selectCount(
            Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBase.getId())
        );
        return new KnowledgeBaseAdminVO(
            knowledgeBase.getId(),
            knowledgeBase.getCode(),
            knowledgeBase.getName(),
            knowledgeBase.getDescription(),
            knowledgeBase.getStatus(),
            knowledgeBase.getVersion(),
            documentCount,
            roles,
            knowledgeBase.getCreatedAt(),
            knowledgeBase.getUpdatedAt()
        );
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("知识库编码不能为空");
        }
        String code = value.trim().toLowerCase(Locale.ROOT);
        if (!code.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || code.length() > 64) {
            throw new IllegalArgumentException("知识库编码只能包含小写字母、数字和连字符");
        }
        return code;
    }

    private String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("知识库名称不能为空");
        }
        return value.trim();
    }

    private Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("当前用户无效");
        }
        return userId;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
