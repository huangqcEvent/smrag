package com.hqc.smrag.dto;

import lombok.Data;
import java.util.List;

/**
 * 聊天响应 DTO
 * <p>
 * 封装聊天服务的响应结果，包括：
 * <ul>
 *     <li>answer：模型生成的答案</li>
 *     <li>routeType：路由类型，表示问题被分发到哪个分支处理</li>
 *     <li>references：引用来源列表（文档名称）</li>
 *     <li>citations：详细引用信息列表</li>
 *     <li>sessionId：会话ID</li>
 * </ul>
 *
 * @see ChatRequestDTO 聊天请求 DTO
 * @see CitationDTO 引用 DTO
 */
@Data
public class ChatResponseDTO {

    /**
     * 模型生成的答案
     */
    private String answer;

    /**
     * 路由类型
     * <p>
     * 可能的值：KNOWLEDGE_QA、PRODUCT_QUERY、PRODUCT_COMPARE、PRODUCT_RECOMMEND、MANAGER_QUERY、SALES_SCRIPT
     */
    private String routeType;

    /**
     * 引用来源列表
     * <p>
     * 答案引用的文档名称列表（去重后）。
     */
    private List<String> references;

    /**
     * 详细引用信息列表
     * <p>
     * 包含向量ID、文档ID、页码、相似度分数、摘要等信息。
     */
    private List<CitationDTO> citations = List.of();

    /**
     * 会话ID
     * <p>
     * 用于后续继续对话。
     */
    private String sessionId;
}
