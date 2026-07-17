package com.hqc.smrag.dto;

/**
 * 引用 DTO
 * <p>
 * 封装答案引用的文档片段信息，用于展示引用来源。
 * 包含的信息：
 * <ul>
 *     <li>vectorId：向量ID，唯一标识向量数据库中的记录</li>
 *     <li>documentId：文档ID</li>
 *     <li>knowledgeBaseId：知识库ID</li>
 *     <li>documentName：文档名称</li>
 *     <li>chunkIndex：分块索引</li>
 *     <li>pageNumber：页码（PDF文档）</li>
 *     <li>score：相似度分数</li>
 *     <li>excerpt：内容摘要</li>
 * </ul>
 *
 * @see ChatResponseDTO 聊天响应 DTO
 * @see CitationAssembler 引用组装器
 */
public record CitationDTO(
    String vectorId,
    Long documentId,
    String knowledgeBaseId,
    String documentName,
    Integer chunkIndex,
    Integer pageNumber,
    Double score,
    String excerpt
) {
}
