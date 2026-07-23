package com.hqc.smrag.controller;

import com.hqc.smrag.service.knowledge.KnowledgeBaseService;
import com.hqc.smrag.vo.KnowledgeBaseVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeBaseControllerTest {

    @Test
    void listsOnlyBasesAccessibleToCurrentUser() {
        KnowledgeBaseService service = mock(KnowledgeBaseService.class);
        KnowledgeBaseVO resource = new KnowledgeBaseVO(
            12L, "research", "投研知识库", "研究资料", "ACTIVE");
        when(service.listAccessible(7L)).thenReturn(List.of(resource));

        KnowledgeBaseController controller = new KnowledgeBaseController(service);

        assertThat(controller.list(7L).getData()).containsExactly(resource);
    }
}
