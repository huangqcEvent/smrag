package com.hqc.smrag.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hqc.smrag.dto.SetDocPermissionRequest;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.entity.SysRole;
import com.hqc.smrag.entity.DocPermission;
import com.hqc.smrag.mapper.DocPermissionMapper;
import com.hqc.smrag.mapper.SysRoleMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.mapper.VectorAclMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocPermissionServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant =
            new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, DocPermission.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocument.class);
    }

    @Test
    void validatesEveryRoleBeforeDeletingExistingPermissions() {
        DocPermissionMapper permissionMapper = mock(DocPermissionMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        DocPermissionService service = new DocPermissionService(
            permissionMapper, roleMapper, mock(KnowledgeDocumentMapper.class),
            mock(VectorAclMapper.class));
        when(roleMapper.selectByCodesForKeyShare(any())).thenReturn(List.of(role("USER")));

        assertThatThrownBy(() -> service.setDocumentPermissions(7L,
            request("USER", "MISSING")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("MISSING");

        verify(permissionMapper, never()).delete(any());
        verify(permissionMapper, never()).insert(any(DocPermission.class));
    }

    @Test
    void rejectsEmptyPermissionsWithoutDeletingExistingGrants() {
        DocPermissionMapper permissionMapper = mock(DocPermissionMapper.class);
        DocPermissionService service = new DocPermissionService(
            permissionMapper, mock(SysRoleMapper.class),
            mock(KnowledgeDocumentMapper.class), mock(VectorAclMapper.class));

        assertThatThrownBy(() ->
            service.setDocumentPermissions(7L, request()))
            .isInstanceOf(IllegalArgumentException.class);

        verify(permissionMapper, never()).delete(any());
    }

    @Test
    void rejectsPermissionEntriesThatDoNotGrantReadAccess() {
        DocPermissionMapper permissionMapper = mock(DocPermissionMapper.class);
        DocPermissionService service = new DocPermissionService(
            permissionMapper, mock(SysRoleMapper.class),
            mock(KnowledgeDocumentMapper.class), mock(VectorAclMapper.class));
        SetDocPermissionRequest request = request("USER");
        request.getPermissions().get(0).setCanRead(false);

        assertThatThrownBy(() -> service.setDocumentPermissions(7L, request))
            .isInstanceOf(IllegalArgumentException.class);

        verify(permissionMapper, never()).delete(any());
    }

    @Test
    void synchronizesAclVersionAndRolesToExistingVectorMetadata() {
        DocPermissionMapper permissionMapper = mock(DocPermissionMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        VectorAclMapper vectorAclMapper = mock(VectorAclMapper.class);
        DocPermissionService service = new DocPermissionService(
            permissionMapper, roleMapper, documentMapper, vectorAclMapper);
        when(roleMapper.selectByCodesForKeyShare(any()))
            .thenReturn(List.of(role(1L, "USER"), role(5L, "AUDITOR")));
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(7L);
        document.setAclVersion(3);
        when(documentMapper.selectByIdForUpdate(7L)).thenReturn(document);

        service.setDocumentPermissions(7L, request("USER", "AUDITOR"));

        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        verify(vectorAclMapper).updateDocumentAcl(eq(7L), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue())
            .contains("\"aclVersion\":4")
            .contains("\"aclDenied\":false")
            .contains("\"aclRoleId_1\":true")
            .contains("\"aclRoleId_5\":true")
            .doesNotContain("aclRole_USER", "aclRole_AUDITOR");
        InOrder order = inOrder(documentMapper, permissionMapper);
        order.verify(documentMapper).selectByIdForUpdate(7L);
        order.verify(permissionMapper).delete(any());
    }

    @Test
    void reconcilesVectorsFromOneLockedAclSnapshot() {
        DocPermissionMapper permissionMapper = mock(DocPermissionMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        VectorAclMapper vectorAclMapper = mock(VectorAclMapper.class);
        DocPermissionService service = new DocPermissionService(
            permissionMapper, mock(SysRoleMapper.class), documentMapper, vectorAclMapper);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(7L);
        document.setAclVersion(6);
        when(documentMapper.selectByIdForUpdate(7L)).thenReturn(document);
        when(permissionMapper.selectReadableRoleIdsByDocumentId(7L))
            .thenReturn(List.of(1L));

        service.synchronizeVectorAcl(7L);

        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        verify(vectorAclMapper).updateDocumentAcl(eq(7L), metadataCaptor.capture());
        assertThat(metadataCaptor.getValue())
            .contains("\"aclVersion\":6")
            .contains("\"aclDenied\":false")
            .contains("\"aclRoleId_1\":true")
            .doesNotContain("aclRole_1");
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
        return role(1L, code);
    }

    private SysRole role(Long id, String code) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(code);
        return role;
    }
}
