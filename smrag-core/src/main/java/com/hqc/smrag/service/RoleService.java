package com.hqc.smrag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hqc.smrag.dto.CreateRoleRequest;
import com.hqc.smrag.dto.UpdateRoleRequest;
import com.hqc.smrag.entity.SysRole;
import com.hqc.smrag.entity.SysUserRole;
import com.hqc.smrag.mapper.DocPermissionMapper;
import com.hqc.smrag.mapper.SysRoleMapper;
import com.hqc.smrag.mapper.SysUserRoleMapper;
import com.hqc.smrag.mapper.KnowledgeBaseRoleMapper;
import com.hqc.smrag.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final DocPermissionMapper docPermissionMapper;
    private final KnowledgeBaseRoleMapper knowledgeBaseRoleMapper;

    public List<RoleVO> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
            .orderByAsc(SysRole::getRoleCode)).stream().map(this::toVO).toList();
    }

    @Transactional
    public Long create(CreateRoleRequest request) {
        String code = request.roleCode().trim().toUpperCase(Locale.ROOT);
        if (roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getRoleCode, code)) > 0) {
            throw new IllegalArgumentException("角色编码已存在: " + code);
        }
        SysRole role = new SysRole();
        role.setRoleCode(code);
        role.setRoleName(request.roleName().trim());
        role.setDescription(trimToNull(request.description()));
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.insert(role);
        return role.getId();
    }

    @Transactional
    public void update(Long id, UpdateRoleRequest request) {
        SysRole role = requireRole(id);
        role.setRoleName(request.roleName().trim());
        role.setDescription(trimToNull(request.description()));
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);
    }

    @Transactional
    public void delete(Long id) {
        SysRole role = roleMapper.selectByIdForUpdate(id);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        if ("ADMIN".equals(role.getRoleCode())) {
            throw new IllegalStateException("系统管理员角色不能删除");
        }
        if (userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
            .eq(SysUserRole::getRoleId, id)) > 0) {
            throw new IllegalStateException("角色仍分配给用户，请先调整用户角色");
        }
        if (docPermissionMapper.countByRoleId(id) > 0) {
            throw new IllegalStateException("角色仍被文档权限使用，请先调整文档权限");
        }
        if (knowledgeBaseRoleMapper.countByRoleId(id) > 0) {
            throw new IllegalStateException("角色仍被知识库权限使用，请先调整知识库权限");
        }
        roleMapper.deleteById(id);
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) throw new IllegalArgumentException("角色不存在");
        return role;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private RoleVO toVO(SysRole role) {
        return new RoleVO(role.getId(), role.getRoleCode(), role.getRoleName(), role.getDescription());
    }
}
