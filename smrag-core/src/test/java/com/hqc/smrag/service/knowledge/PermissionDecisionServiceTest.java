package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.entity.KnowledgeBase;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.mapper.DocPermissionMapper;
import com.hqc.smrag.mapper.KnowledgeBaseMapper;
import com.hqc.smrag.mapper.KnowledgeBaseRoleMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionDecisionServiceTest {

    @Test
    void requiresBothKnowledgeBaseAndDocumentRoleIntersections() {
        Fixture fixture = new Fixture();
        fixture.activeDocument();
        when(fixture.baseRoleMapper.selectRoleIds(7L)).thenReturn(List.of(2L));
        when(fixture.permissionMapper.selectReadableRoleIdsByDocumentId(42L))
            .thenReturn(List.of(3L));
        when(fixture.permissionMapper.selectRoleIdsByUserId(9L))
            .thenReturn(List.of(2L, 3L));

        assertThat(fixture.service.canReadDocument(9L, 42L)).isTrue();

        when(fixture.permissionMapper.selectRoleIdsByUserId(9L))
            .thenReturn(List.of(2L));
        assertThat(fixture.service.canReadDocument(9L, 42L)).isFalse();
    }

    @Test
    void emptyAclBlocksEvenAGlobalAdmin() {
        Fixture fixture = new Fixture();
        fixture.activeDocument();
        when(fixture.userRoleMapper.userHasRole(1L, "ADMIN")).thenReturn(true);
        when(fixture.baseRoleMapper.selectRoleIds(7L)).thenReturn(List.of(1L));
        when(fixture.permissionMapper.selectReadableRoleIdsByDocumentId(42L))
            .thenReturn(List.of());

        assertThat(fixture.service.canReadDocument(1L, 42L)).isFalse();
    }

    @Test
    void emptyKnowledgeBaseAclBlocksEvenAGlobalAdmin() {
        Fixture fixture = new Fixture();
        KnowledgeBase base = new KnowledgeBase();
        base.setId(7L);
        base.setStatus(KnowledgeBaseService.ACTIVE);
        when(fixture.baseMapper.selectById(7L)).thenReturn(base);
        when(fixture.userRoleMapper.userHasRole(1L, "ADMIN")).thenReturn(true);
        when(fixture.baseRoleMapper.selectRoleIds(7L)).thenReturn(List.of());

        assertThat(fixture.service.canAccessKnowledgeBase(1L, 7L)).isFalse();
    }

    @Test
    void documentRolesMustBeASubsetOfKnowledgeBaseRoles() {
        Fixture fixture = new Fixture();
        when(fixture.baseRoleMapper.selectRoleIds(7L)).thenReturn(List.of(2L, 3L));

        assertThatThrownBy(() ->
            fixture.service.validateDocumentRoles(7L, List.of(2L, 5L)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("5");
    }

    private static final class Fixture {
        private final KnowledgeBaseMapper baseMapper = mock(KnowledgeBaseMapper.class);
        private final KnowledgeBaseRoleMapper baseRoleMapper =
            mock(KnowledgeBaseRoleMapper.class);
        private final KnowledgeDocumentMapper documentMapper =
            mock(KnowledgeDocumentMapper.class);
        private final DocPermissionMapper permissionMapper =
            mock(DocPermissionMapper.class);
        private final SysUserRoleMapper userRoleMapper =
            mock(SysUserRoleMapper.class);
        private final PermissionDecisionService service =
            new PermissionDecisionService(
                baseMapper,
                baseRoleMapper,
                documentMapper,
                permissionMapper,
                userRoleMapper
            );

        private void activeDocument() {
            KnowledgeDocument document = new KnowledgeDocument();
            document.setId(42L);
            document.setKnowledgeBaseId(7L);
            when(documentMapper.selectById(42L)).thenReturn(document);
            KnowledgeBase base = new KnowledgeBase();
            base.setId(7L);
            base.setStatus(KnowledgeBaseService.ACTIVE);
            when(baseMapper.selectById(7L)).thenReturn(base);
        }
    }
}
