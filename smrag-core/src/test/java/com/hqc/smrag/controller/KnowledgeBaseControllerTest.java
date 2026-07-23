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
    void listsActiveKnowledgeBases() {
        KnowledgeBaseService service = mock(KnowledgeBaseService.class);
        when(service.listActive()).thenReturn(List.of(
            new KnowledgeBaseVO("company-main", "企业知识库", "企业内部统一知识库")
        ));

        KnowledgeBaseController controller = new KnowledgeBaseController(service);

        assertThat(controller.list().getData()).containsExactly(
            new KnowledgeBaseVO("company-main", "企业知识库", "企业内部统一知识库")
        );
    }
}
