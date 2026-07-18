package com.hqc.smrag.service;

import com.hqc.smrag.dto.SetDocPermissionRequest;
import com.hqc.smrag.entity.SysRole;
import com.hqc.smrag.entity.DocPermission;
import com.hqc.smrag.mapper.DocPermissionMapper;
import com.hqc.smrag.mapper.SysRoleMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocPermissionServiceTest {

    @Test
    void validatesEveryRoleBeforeDeletingExistingPermissions() {
        DocPermissionMapper permissionMapper = mock(DocPermissionMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        DocPermissionService service = new DocPermissionService(permissionMapper, roleMapper);
        when(roleMapper.selectList(any())).thenReturn(List.of(role("USER")));

        assertThatThrownBy(() -> service.setDocumentPermissions(7L,
            request("USER", "MISSING")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("MISSING");

        verify(permissionMapper, never()).delete(any());
        verify(permissionMapper, never()).insert(any(DocPermission.class));
    }

    private SetDocPermissionRequest request(String... codes) {
        SetDocPermissionRequest request = new SetDocPermissionRequest();
        request.setPermissions(java.util.Arrays.stream(codes).map(code -> {
            SetDocPermissionRequest.RolePermission permission =
                new SetDocPermissionRequest.RolePermission();
            permission.setRoleCode(code);
            permission.setCanRead(true);
            return permission;
        }).toList());
        return request;
    }

    private SysRole role(String code) {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setRoleCode(code);
        return role;
    }
}
