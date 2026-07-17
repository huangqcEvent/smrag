package com.hqc.smrag.enums;

import lombok.Getter;

/**
 * 文档状态枚举
 * <p>
 * 定义知识库文档的处理状态。
 * <p>
 * 状态流转：
 * <ul>
 *     <li>PENDING（待处理）→ PROCESSING（处理中）→ COMPLETED（已完成）</li>
 *     <li>PENDING → PROCESSING → FAILED（失败）→ PROCESSING（重试）</li>
 *     <li>PENDING → DELETING（删除中）→ 已删除</li>
 * </ul>
 *
 * @see com.hqc.smrag.entity.KnowledgeDocument 知识库文档实体
 */
@Getter
public enum DocumentStatus {

    /**
     * 待处理
     * <p>
     * 文档已上传，等待 Kafka 消费者处理。
     */
    PENDING("待处理"),

    /**
     * 处理中
     * <p>
     * 文档正在被解析和向量化。
     */
    PROCESSING("处理中"),

    /**
     * 删除中
     * <p>
     * 文档正在被删除，包括删除向量数据和分块记录。
     */
    DELETING("删除中"),

    /**
     * 已完成
     * <p>
     * 文档已成功解析并完成向量化，可以进行检索。
     */
    COMPLETED("已完成"),

    /**
     * 失败
     * <p>
     * 文档处理失败，可能需要重试或人工干预。
     */
    FAILED("失败");

    /**
     * 状态描述
     */
    private final String desc;

    DocumentStatus(String desc) {
        this.desc = desc;
    }
}
