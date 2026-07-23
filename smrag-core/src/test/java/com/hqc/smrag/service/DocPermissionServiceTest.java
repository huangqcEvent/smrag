package com.hqc.smrag.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hqc.smrag.dto.SetDocPermissionRequest;
import com.hqc.smrag.entity.DocPermission;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.entity.KnowledgeBase;
import com.hqc.smrag.entity.SysRole;
import com.hqc.smrag.mapper.DocPermissionMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.mapper.KnowledgeBaseMapper;
import com.hqc.smrag.mapper.SysRoleMapper;
import com.hqc.smrag.mapper.VectorAclMapper;
import com.hqc.smrag.service.knowledge.PermissionDecisionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

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
        Fixture fixture = new Fixture();
        when(fixture.roleMapper.selectByCodesForKeyShare(any()))
            .thenReturn(List.of(role(1L, "USER")));

        assertThatThrownBy(() -> fixture.service.setDocumentPermissions(
            7L, request("USER", "MISSING")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("MISSING");

        verify(fixture.permissionMapper, never()).delete(any());
    }

    @Test
    void rejectsGlobalAdministratorAsADocumentAclRole() {
        Fixture fixture = new Fixture();

        assertThatThrownBy(() -> fixture.service.setDocumentPermissions(
            7L, request("ADMIN", "USER")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ADMIN");

        verify(fixture.roleMapper, never()).selectByCodesForKeyShare(any());
        verify(fixture.permissionMapper, never()).delete(any());
    }

    @Test
    void rejectsRolesOutsideKnowledgeBaseBeforeReplacingGrants() {
        Fixture fixture = new Fixture();
        when(fixture.roleMapper.selectByCodesForKeyShare(any()))
            .thenReturn(List.of(role(5L, "AUDITOR")));
        when(fixture.documentMapper.selectById(7L)).thenReturn(document());
        when(fixture.baseMapper.selectByIdForUpdate(1L)).thenReturn(base());
        when(fixture.documentMapper.selectByIdForUpdate(7L))
            .thenReturn(document());
        org.mockito.Mockito.doThrow(new IllegalStateException("不属于知识库"))
            .when(fixture.decisions)
            .validateDocumentRoles(1L, List.of(5L));

        assertThatThrownBy(() -> fixture.service.setDocumentPermissions(
            7L, request("AUDITOR")))
            .isInstanceOf(IllegalStateException.class);

        verify(fixture.permissionMapper, never()).delete(any());
    }

    @Test
    void storesRelationalGrantsAndOnlyWritesAclVersionToMetadata() {
        Fixture fixture = new Fixture();
        when(fixture.roleMapper.selectByCodesForKeyShare(any()))
            .thenReturn(List.of(role(1L, "USER"), role(5L, "AUDITOR")));
        when(fixture.documentMapper.selectById(7L)).thenReturn(document());
        when(fixture.baseMapper.selectByIdForUpdate(1L)).thenReturn(base());
        when(fixture.documentMapper.selectByIdForUpdate(7L))
            .thenReturn(document());

        fixture.service.setDocumentPermissions(
            7L, request("USER", "AUDITOR"));

        verify(fixture.decisions)
            .validateDocumentRoles(1L, List.of(1L, 5L));
        verify(fixture.permissionMapper).delete(any());
        verify(fixture.permissionMapper, org.mockito.Mockito.times(2))
            .insert(any(DocPermission.class));
        verify(fixture.vectorMapper).updateDocumentAcl(7L, 4);
        var order = inOrder(fixture.baseMapper, fixture.documentMapper);
        order.verify(fixture.baseMapper).selectByIdForUpdate(1L);
        order.verify(fixture.documentMapper).selectByIdForUpdate(7L);
    }

    @Test
    void synchronizesExistingVectorsFromCurrentRelationalSnapshot() {
        Fixture fixture = new Fixture();
        KnowledgeDocument document = document();
        document.setAclVersion(6);
        when(fixture.documentMapper.selectById(7L)).thenReturn(document);
        when(fixture.baseMapper.selectByIdForUpdate(1L)).thenReturn(base());
        when(fixture.documentMapper.selectByIdForUpdate(7L))
            .thenReturn(document);
        when(fixture.permissionMapper.selectReadableRoleIdsByDocumentId(7L))
            .thenReturn(List.of(1L));

        fixture.service.synchronizeVectorAcl(7L);

        verify(fixture.decisions).validateDocumentRoles(1L, List.of(1L));
        verify(fixture.vectorMapper).updateDocumentAcl(eq(7L), eq(6));
    }

    private static KnowledgeDocument document() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(7L);
        document.setKnowledgeBaseId(1L);
        document.setAclVersion(3);
        return document;
    }

    private static KnowledgeBase base() {
        KnowledgeBase base = new KnowledgeBase();
        base.setId(1L);
        base.setStatus("ACTIVE");
        return base;
    }

    private static SetDocPermissionRequest request(String... codes) {
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

    private static SysRole role(Long id, String code) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(code);
        return role;
    }

    private static final class Fixture {
        private final DocPermissionMapper permissionMapper =
            mock(DocPermissionMapper.class);
        private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        private final KnowledgeDocumentMapper documentMapper =
            mock(KnowledgeDocumentMapper.class);
        private final KnowledgeBaseMapper baseMapper =
            mock(KnowledgeBaseMapper.class);
        private final VectorAclMapper vectorMapper = mock(VectorAclMapper.class);
        private final PermissionDecisionService decisions =
            mock(PermissionDecisionService.class);
        private final DocPermissionService service = new DocPermissionService(
            permissionMapper,
            roleMapper,
            documentMapper,
            baseMapper,
            vectorMapper,
            decisions
        );
    }
}
