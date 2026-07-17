package com.hqc.smrag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文档解析 Outbox 实体
 * <p>
 * 对应数据库表 document_ingestion_outbox，用于实现 Outbox 模式。
 * <p>
 * Outbox 模式是一种确保数据库操作和消息发送一致性的设计模式：
 * <ol>
 *     <li>在同一事务中，将业务数据（文档记录）和消息（Outbox 记录）一起写入数据库</li>
 *     <li>Kafka 消费者从 Outbox 表中读取待处理任务</li>
 *     <li>处理完成后删除或更新 Outbox 记录</li>
 * </ol>
 * <p>
 * 主要字段：
 * <ul>
 *     <li>id：主键（UUID）</li>
 *     <li>documentId：文档ID</li>
 *     <li>payload：任务载荷（JSON格式）</li>
 *     <li>status：状态（PENDING、PROCESSING、COMPLETED、FAILED）</li>
 *     <li>attemptCount：尝试次数</li>
 *     <li>nextAttemptAt：下次尝试时间</li>
 *     <li>lastError：最后错误信息</li>
 * </ul>
 *
 * @see com.hqc.smrag.dto.DocumentParseTaskDTO 文档解析任务 DTO
 * @see com.hqc.smrag.service.knowledge.DocumentUploadPersistenceService 文档上传持久化服务
 */
@Data
@TableName("document_ingestion_outbox")
public class DocumentIngestionOutbox {

    /**
     * 主键ID（UUID）
     */
    @TableId(type = IdType.INPUT)
    private UUID id;

    /**
     * 文档ID
     * <p>
     * 关联 knowledge_document 表的 id 字段。
     */
    private Long documentId;

    /**
     * 任务载荷
     * <p>
     * JSON 格式的 DocumentParseTaskDTO，包含解析任务的所有信息。
     */
    private String payload;

    /**
     * 状态
     * <p>
     * 可能的值：PENDING（待处理）、PROCESSING（处理中）、COMPLETED（已完成）、FAILED（失败）。
     */
    private String status;

    /**
     * 尝试次数
     * <p>
     * 记录任务已经尝试处理的次数，用于重试控制。
     */
    private Integer attemptCount;

    /**
     * 下次尝试时间
     * <p>
     * 用于实现延迟重试，失败后延迟一段时间再重试。
     */
    private LocalDateTime nextAttemptAt;

    /**
     * 最后错误信息
     * <p>
     * 记录最近一次处理失败的错误信息。
     */
    private String lastError;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 发布时间
     * <p>
     * 任务被 Kafka 消费者拉取处理的时间。
     */
    private LocalDateTime publishedAt;
}
