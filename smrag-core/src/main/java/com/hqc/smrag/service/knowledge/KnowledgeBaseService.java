package com.hqc.smrag.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hqc.smrag.entity.KnowledgeBase;
import com.hqc.smrag.mapper.KnowledgeBaseMapper;
import com.hqc.smrag.vo.KnowledgeBaseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private static final String ACTIVE = "ACTIVE";
    private final KnowledgeBaseMapper mapper;

    public List<KnowledgeBaseVO> listActive() {
        return mapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getStatus, ACTIVE)
                .orderByAsc(KnowledgeBase::getCreatedAt)
                .orderByAsc(KnowledgeBase::getCode))
            .stream()
            .map(this::toView)
            .toList();
    }

    public String requireActiveCode(String value) {
        String code = KnowledgeBaseDefaults.normalizeCode(value);
        KnowledgeBase knowledgeBase = mapper.selectById(code);
        if (knowledgeBase == null) {
            throw new IllegalArgumentException("知识库不存在: " + code);
        }
        if (!ACTIVE.equals(knowledgeBase.getStatus())) {
            throw new IllegalArgumentException("知识库未启用: " + code);
        }
        return code;
    }

    private KnowledgeBaseVO toView(KnowledgeBase knowledgeBase) {
        return new KnowledgeBaseVO(
            knowledgeBase.getCode(),
            knowledgeBase.getName(),
            knowledgeBase.getDescription()
        );
    }
}
