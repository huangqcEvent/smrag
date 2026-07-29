package com.hqc.smrag.controller;

import com.hqc.smrag.common.PageResult;
import com.hqc.smrag.entity.KnowledgeBase;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.service.DocPermissionService;
import com.hqc.smrag.service.knowledge.DocumentFileService;
import com.hqc.smrag.service.knowledge.DocumentService;
import com.hqc.smrag.service.knowledge.KnowledgeBaseService;
import com.hqc.smrag.service.knowledge.PermissionDecisionService;
import com.hqc.smrag.vo.DocumentVO;
import com.hqc.smrag.vo.RoleVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgePermissionListTest {

    @Test
    void returnsPagedDocumentsWithCurrentAccessAndGrantedRoles() {
        DocumentService documentService = mock(DocumentService.class);
        DocPermissionService permissionService = mock(DocPermissionService.class);
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        PermissionDecisionService decisions = mock(PermissionDecisionService.class);
        KnowledgeController controller = new KnowledgeController(
            documentService,
            permissionService,
            mock(DocumentFileService.class),
            knowledgeBaseService,
            decisions
        );
        KnowledgeDocument readable = document(1L, "readable.pdf");
        KnowledgeDocument locked = document(2L, "locked.pdf");
        RoleVO userRole = new RoleVO(2L, "USER", "普通用户", null);
        RoleVO auditRole = new RoleVO(3L, "AUDIT", "审计", null);

        KnowledgeBase base = new KnowledgeBase();
        base.setId(12L);
        base.setCode("research");
        when(knowledgeBaseService.requireByCode("research")).thenReturn(base);
        when(documentService.listDocuments("research", 2, 20, "quarterly", "COMPLETED"))
            .thenReturn(new PageResult<>(List.of(readable, locked), 42, 2, 20, true));
        when(permissionService.getAccessibleDocIds(10L, List.of(1L, 2L)))
            .thenReturn(List.of(1L));
        when(permissionService.getReadableRolesByDocumentIds(List.of(1L, 2L)))
            .thenReturn(Map.of(1L, List.of(userRole), 2L, List.of(auditRole)));

        PageResult<DocumentVO> result =
            controller.list("research", 2, 20, "quarterly", "COMPLETED", 10L).getData();

        assertThat(result.total()).isEqualTo(42);
        assertThat(result.pageNum()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.records()).hasSize(2);
        assertThat(result.records().get(0).isAccessible()).isTrue();
        assertThat(result.records().get(0).getAllowedRoles()).extracting(RoleVO::roleCode)
            .containsExactly("USER");
        assertThat(result.records().get(1).isAccessible()).isFalse();
        assertThat(result.records().get(1).getAllowedRoles()).extracting(RoleVO::roleName)
            .containsExactly("审计");
    }

    private KnowledgeDocument document(Long id, String name) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(id);
        document.setName(name);
        return document;
    }
}
