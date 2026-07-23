package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.entity.KnowledgeBase;
import com.hqc.smrag.mapper.KnowledgeBaseMapper;
import com.hqc.smrag.vo.KnowledgeBaseVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeBaseServiceTest {

    private final KnowledgeBaseMapper mapper = mock(KnowledgeBaseMapper.class);
    private final KnowledgeBaseService service = new KnowledgeBaseService(mapper);

    @Test
    void normalizesBlankAndLegacyDefaultToCompanyMain() {
        assertThat(KnowledgeBaseDefaults.normalizeCode(null)).isEqualTo("company-main");
        assertThat(KnowledgeBaseDefaults.normalizeCode(" ")).isEqualTo("company-main");
        assertThat(KnowledgeBaseDefaults.normalizeCode("default")).isEqualTo("company-main");
        assertThat(KnowledgeBaseDefaults.normalizeCode(" FINANCE ")).isEqualTo("finance");
    }

    @Test
    void rejectsInvalidKnowledgeBaseCode() {
        assertThatThrownBy(() -> KnowledgeBaseDefaults.normalizeCode("../finance"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("知识库 code");
    }

    @Test
    void requiresKnowledgeBaseToExistAndBeActive() {
        KnowledgeBase active = knowledgeBase("company-main", "企业知识库", "ACTIVE");
        KnowledgeBase disabled = knowledgeBase("archive", "归档知识库", "DISABLED");
        when(mapper.selectById("company-main")).thenReturn(active);
        when(mapper.selectById("archive")).thenReturn(disabled);

        assertThat(service.requireActiveCode("default")).isEqualTo("company-main");
        assertThatThrownBy(() -> service.requireActiveCode("archive"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未启用");
        assertThatThrownBy(() -> service.requireActiveCode("missing"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不存在");
    }

    @Test
    void listsOnlyActiveKnowledgeBasesAsViewObjects() {
        when(mapper.selectList(any())).thenReturn(List.of(
            knowledgeBase("company-main", "企业知识库", "ACTIVE")
        ));

        List<KnowledgeBaseVO> result = service.listActive();

        assertThat(result).containsExactly(
            new KnowledgeBaseVO("company-main", "企业知识库", "企业内部统一知识库")
        );
    }

    private KnowledgeBase knowledgeBase(String code, String name, String status) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setCode(code);
        knowledgeBase.setName(name);
        knowledgeBase.setDescription("company-main".equals(code) ? "企业内部统一知识库" : null);
        knowledgeBase.setStatus(status);
        return knowledgeBase;
    }
}
