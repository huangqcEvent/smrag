package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.dto.MoveDocumentRequest;
import com.hqc.smrag.entity.KnowledgeBase;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.mapper.DocPermissionMapper;
import com.hqc.smrag.mapper.KnowledgeBaseMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.mapper.VectorAclMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentTransferServiceTest {

    @Test
    void movesRelationalAndVectorOwnershipWithoutReindexing() {
        Fixture fixture = new Fixture();
        KnowledgeDocument document = fixture.document();
        KnowledgeBase source = base(3L, KnowledgeBaseService.ACTIVE);
        KnowledgeBase target = base(8L, KnowledgeBaseService.ACTIVE);
        when(fixture.documentMapper.selectById(42L)).thenReturn(document);
        when(fixture.documentMapper.selectByIdForUpdate(42L)).thenReturn(document);
        when(fixture.baseService.requireActive("research")).thenReturn(target);
        when(fixture.baseMapper.selectByIdForUpdate(3L)).thenReturn(source);
        when(fixture.baseMapper.selectByIdForUpdate(8L)).thenReturn(target);
        when(fixture.permissionMapper.selectReadableRoleIdsByDocumentId(42L))
            .thenReturn(List.of(2L));

        fixture.service.move(42L, new MoveDocumentRequest("research"));

        verify(fixture.permissionDecisionService)
            .validateDocumentRoles(8L, List.of(2L));
        verify(fixture.documentMapper).updateById(
            org.mockito.ArgumentMatchers.<KnowledgeDocument>argThat(updated ->
                updated.getKnowledgeBaseId().equals(8L)
            )
        );
        verify(fixture.vectorMapper).updateKnowledgeBase(42L, 8L);
    }

    @Test
    void rechecksTargetStatusAfterAcquiringLocks() {
        Fixture fixture = new Fixture();
        KnowledgeDocument document = fixture.document();
        KnowledgeBase targetBeforeLock = base(8L, KnowledgeBaseService.ACTIVE);
        when(fixture.documentMapper.selectById(42L)).thenReturn(document);
        when(fixture.documentMapper.selectByIdForUpdate(42L)).thenReturn(document);
        when(fixture.baseService.requireActive("research"))
            .thenReturn(targetBeforeLock);
        when(fixture.baseMapper.selectByIdForUpdate(3L))
            .thenReturn(base(3L, KnowledgeBaseService.ACTIVE));
        when(fixture.baseMapper.selectByIdForUpdate(8L))
            .thenReturn(base(8L, KnowledgeBaseService.DISABLED));

        assertThatThrownBy(() ->
            fixture.service.move(42L, new MoveDocumentRequest("research")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("停用");
        verify(fixture.vectorMapper, never()).updateKnowledgeBase(42L, 8L);
    }

    @Test
    void rejectsPendingDocumentsSoTheQueuedParseTaskCannotBecomeStale() {
        Fixture fixture = new Fixture();
        KnowledgeDocument document = fixture.document();
        document.setStatus("PENDING");
        when(fixture.documentMapper.selectById(42L)).thenReturn(document);
        when(fixture.baseService.requireActive("research"))
            .thenReturn(base(8L, KnowledgeBaseService.ACTIVE));
        when(fixture.baseMapper.selectByIdForUpdate(3L))
            .thenReturn(base(3L, KnowledgeBaseService.ACTIVE));
        when(fixture.baseMapper.selectByIdForUpdate(8L))
            .thenReturn(base(8L, KnowledgeBaseService.ACTIVE));
        when(fixture.documentMapper.selectByIdForUpdate(42L)).thenReturn(document);

        assertThatThrownBy(() ->
            fixture.service.move(42L, new MoveDocumentRequest("research")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("处理");

        verify(fixture.vectorMapper, never()).updateKnowledgeBase(42L, 8L);
    }

    private static KnowledgeBase base(Long id, String status) {
        KnowledgeBase base = new KnowledgeBase();
        base.setId(id);
        base.setStatus(status);
        return base;
    }

    private static final class Fixture {
        private final KnowledgeDocumentMapper documentMapper =
            mock(KnowledgeDocumentMapper.class);
        private final KnowledgeBaseMapper baseMapper = mock(KnowledgeBaseMapper.class);
        private final DocPermissionMapper permissionMapper =
            mock(DocPermissionMapper.class);
        private final VectorAclMapper vectorMapper = mock(VectorAclMapper.class);
        private final KnowledgeBaseService baseService =
            mock(KnowledgeBaseService.class);
        private final PermissionDecisionService permissionDecisionService =
            mock(PermissionDecisionService.class);
        private final DocumentTransferService service = new DocumentTransferService(
            documentMapper,
            baseMapper,
            permissionMapper,
            vectorMapper,
            baseService,
            permissionDecisionService
        );

        private KnowledgeDocument document() {
            KnowledgeDocument document = new KnowledgeDocument();
            document.setId(42L);
            document.setKnowledgeBaseId(3L);
            document.setStatus("COMPLETED");
            return document;
        }
    }
}
