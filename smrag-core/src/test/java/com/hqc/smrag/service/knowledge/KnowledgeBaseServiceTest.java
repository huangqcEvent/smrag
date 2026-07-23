package com.hqc.smrag.service.knowledge;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hqc.smrag.dto.CreateKnowledgeBaseRequest;
import com.hqc.smrag.dto.SetKnowledgeBaseRolesRequest;
import com.hqc.smrag.entity.KnowledgeBase;
import com.hqc.smrag.entity.KnowledgeBaseRole;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.entity.SysRole;
import com.hqc.smrag.mapper.DocPermissionMapper;
import com.hqc.smrag.mapper.KnowledgeBaseMapper;
import com.hqc.smrag.mapper.KnowledgeBaseRoleMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.mapper.SysRoleMapper;
import com.hqc.smrag.mapper.SysUserRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseServiceTest {

    @BeforeAll
    static void initializeMetadata() {
        MapperBuilderAssistant assistant =
            new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, KnowledgeBase.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocument.class);
    }

    @Test
    void createsAnActiveBaseAndItsInitialRoleSetAtomically() {
        Fixture fixture = new Fixture();
        SysRole role = new SysRole();
        role.setId(2L);
        role.setRoleCode("USER");
        when(fixture.roleMapper.selectBatchIds(any()))
            .thenReturn(List.of(role));
        when(fixture.baseMapper.insert(
            (KnowledgeBase) any(KnowledgeBase.class))).thenAnswer(invocation -> {
            KnowledgeBase base = invocation.getArgument(0);
            base.setId(12L);
            return 1;
        });

        fixture.service.create(new CreateKnowledgeBaseRequest(
            "research",
            "投研知识库",
            "研究资料",
            List.of(2L)
        ), 1L);

        verify(fixture.baseMapper).insert(
            (KnowledgeBase) any(KnowledgeBase.class));
        verify(fixture.baseRoleMapper).insert(
            org.mockito.ArgumentMatchers.argThat((KnowledgeBaseRole relation) ->
                relation.getKnowledgeBaseId().equals(12L)
                    && relation.getRoleId().equals(2L)
            )
        );
    }

    @Test
    void rejectsGlobalAdministratorAsAKnowledgeBaseAclRole() {
        Fixture fixture = new Fixture();
        SysRole role = new SysRole();
        role.setId(1L);
        role.setRoleCode("ADMIN");
        when(fixture.roleMapper.selectBatchIds(any())).thenReturn(List.of(role));

        assertThatThrownBy(() -> fixture.service.create(
            new CreateKnowledgeBaseRequest(
                "research",
                "投研知识库",
                "研究资料",
                List.of(1L)
            ),
            1L
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ADMIN");

        verify(fixture.baseMapper, never()).insert(any(KnowledgeBase.class));
    }

    @Test
    void rejectsRemovingARoleStillUsedByDocuments() {
        Fixture fixture = new Fixture();
        KnowledgeBase base = base(12L, 3);
        SysRole role = new SysRole();
        role.setId(2L);
        when(fixture.baseMapper.selectByIdForUpdate(12L)).thenReturn(base);
        when(fixture.roleMapper.selectBatchIds(any()))
            .thenReturn(List.of(role));
        when(fixture.permissionMapper.selectReadableRoleIdsByKnowledgeBaseId(12L))
            .thenReturn(List.of(2L, 5L));

        assertThatThrownBy(() -> fixture.service.replaceRoles(
            12L,
            new SetKnowledgeBaseRolesRequest(List.of(2L), 3),
            1L
        )).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("5");

        verify(fixture.baseRoleMapper, never()).deleteByKnowledgeBaseId(any());
    }

    @Test
    void rejectsDeletingANonEmptyBase() {
        Fixture fixture = new Fixture();
        when(fixture.baseMapper.selectByIdForUpdate(12L)).thenReturn(base(12L, 1));
        when(fixture.documentMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> fixture.service.delete(12L, 1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("2");
        verify(fixture.baseMapper, never()).deleteById(12L);
    }

    private static KnowledgeBase base(Long id, int version) {
        KnowledgeBase base = new KnowledgeBase();
        base.setId(id);
        base.setCode("research");
        base.setName("投研知识库");
        base.setStatus("ACTIVE");
        base.setVersion(version);
        return base;
    }

    private static final class Fixture {
        private final KnowledgeBaseMapper baseMapper =
            mock(KnowledgeBaseMapper.class);
        private final KnowledgeBaseRoleMapper baseRoleMapper =
            mock(KnowledgeBaseRoleMapper.class);
        private final KnowledgeDocumentMapper documentMapper =
            mock(KnowledgeDocumentMapper.class);
        private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        private final SysUserRoleMapper userRoleMapper =
            mock(SysUserRoleMapper.class);
        private final DocPermissionMapper permissionMapper =
            mock(DocPermissionMapper.class);
        private final KnowledgeBaseService service = new KnowledgeBaseService(
            baseMapper,
            baseRoleMapper,
            documentMapper,
            roleMapper,
            userRoleMapper,
            permissionMapper
        );
    }
}
