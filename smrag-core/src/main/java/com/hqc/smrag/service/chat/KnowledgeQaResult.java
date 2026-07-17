package com.hqc.smrag.service.chat;

import com.hqc.smrag.dto.CitationDTO;

import java.util.List;

/**
 * 知识库问答结果记录类
 * <p>
 * 封装知识库问答的返回结果，包含：
 * <ul>
 *     <li>答案内容：基于检索文档生成的答案</li>
 *     <li>引用来源：答案所引用的文档片段列表</li>
 * </ul>
 *
 * @see KnowledgeQaService 知识库问答服务
 * @see CitationDTO 引用 DTO
 */
public record KnowledgeQaResult(String answer, List<CitationDTO> citations) {

    /**
     * 构造函数，确保非空值
     *
     * @param answer    答案内容
     * @param citations 引用来源列表
     */
    public KnowledgeQaResult {
        answer = answer == null ? "" : answer;
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
