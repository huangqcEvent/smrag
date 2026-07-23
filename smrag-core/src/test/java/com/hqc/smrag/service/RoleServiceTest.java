package com.hqc.smrag.service;

import com.hqc.smrag.dto.CreateRoleRequest;
import com.hqc.smrag.dto.UpdateRoleRequest;
import com.hqc.smrag.entity.SysRole;
import com.hqc.smrag.mapper.DocPermissionMapper;
import com.hqc.smrag.mapper.SysRoleMapper;
import com.hqc.smrag.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RoleServiceTest {

    @Test
    void createsNormalizedCustomRole() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        RoleService service = new RoleService(
            roleMapper, mock(SysUserRoleMapper.class), mock(DocPermissionMapper.class));
        when(roleMapper.selectCount(any())).thenReturn(0L);

        service.create(new CreateRoleRequest("audit_team", "审计组", "只读审计"));

        verify(roleMapper).insert(argThat((SysRole role) ->
            "AUDIT_TEAM".equals(role.getRoleCode()) && "审计组".equals(role.getRoleName())));
    }

    @Test
    void protectsAdminAndRolesAssignedToUsersFromDeletion() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        RoleService service = new RoleService(
            roleMapper, userRoleMapper, mock(DocPermissionMapper.class));
        when(roleMapper.selectByIdForUpdate(1L)).thenReturn(role(1L, "ADMIN"));
        when(roleMapper.selectByIdForUpdate(2L)).thenReturn(role(2L, "AUDIT"));
        when(userRoleMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.delete(2L)).isInstanceOf(IllegalStateException.class);
        verify(roleMapper, never()).deleteById(anyLong());
    }

    @Test
    void updatesOnlyRoleMetadata() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        RoleService service = new RoleService(
            roleMapper, mock(SysUserRoleMapper.class), mock(DocPermissionMapper.class));
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "AUDIT"));

        service.update(2L, new UpdateRoleRequest("新名称", "新描述"));

        verify(roleMapper).updateById(argThat((SysRole role) -> "AUDIT".equals(role.getRoleCode())
            && "新名称".equals(role.getRoleName())));
    }

    @Test
    void rejectsDeletingRoleStillUsedByDocumentPermissions() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        DocPermissionMapper permissionMapper = mock(DocPermissionMapper.class);
        RoleService service = new RoleService(
            roleMapper, mock(SysUserRoleMapper.class), permissionMapper);
        when(roleMapper.selectByIdForUpdate(2L)).thenReturn(role(2L, "AUDIT"));
        when(permissionMapper.countByRoleId(2L)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(2L))
            .isInstanceOf(IllegalStateException.class);

        verify(roleMapper, never()).deleteById(anyLong());
    }

    private SysRole role(Long id, String code) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(code);
        return role;
    }
}
