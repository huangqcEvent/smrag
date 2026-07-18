package com.hqc.smrag.controller;

import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.service.DocPermissionService;
import com.hqc.smrag.service.knowledge.DocumentService;
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
    void returnsAllDocumentsWithCurrentAccessAndGrantedRoles() {
        DocumentService documentService = mock(DocumentService.class);
        DocPermissionService permissionService = mock(DocPermissionService.class);
        KnowledgeController controller = new KnowledgeController(documentService, permissionService);
        KnowledgeDocument readable = document(1L, "readable.pdf");
        KnowledgeDocument locked = document(2L, "locked.pdf");
        RoleVO userRole = new RoleVO(2L, "USER", "普通用户", null);
        RoleVO auditRole = new RoleVO(3L, "AUDIT", "审计", null);

        when(documentService.listDocuments("default")).thenReturn(List.of(readable, locked));
        when(permissionService.getAccessibleDocIds(10L)).thenReturn(List.of(1L));
        when(permissionService.getReadableRolesByDocumentIds(List.of(1L, 2L)))
            .thenReturn(Map.of(1L, List.of(userRole), 2L, List.of(auditRole)));

        List<DocumentVO> result = controller.list("default", 10L).getData();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isAccessible()).isTrue();
        assertThat(result.get(0).getAllowedRoles()).extracting(RoleVO::roleCode)
            .containsExactly("USER");
        assertThat(result.get(1).isAccessible()).isFalse();
        assertThat(result.get(1).getAllowedRoles()).extracting(RoleVO::roleName)
            .containsExactly("审计");
    }

    private KnowledgeDocument document(Long id, String name) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(id);
        document.setName(name);
        return document;
    }
}
