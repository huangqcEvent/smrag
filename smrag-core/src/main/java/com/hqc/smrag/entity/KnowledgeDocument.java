package com.hqc.smrag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 知识库文档实体
 * <p>
 * 对应数据库表 knowledge_document，存储文档的基本信息和处理状态。
 * 主要字段：
 * <ul>
 *     <li>id：主键</li>
 *     <li>name：文档名称</li>
 *     <li>filePath：文件存储路径</li>
 *     <li>fileType：文件类型（PDF、WORD、TXT）</li>
 *     <li>status：处理状态（PENDING、PROCESSING、COMPLETED、FAILED、DELETING）</li>
 *     <li>chunkCount：分块数量</li>
 *     <li>knowledgeBaseId：知识库ID</li>
 *     <li>checksum：文件校验和（SHA-256）</li>
 *     <li>version：版本号，用于乐观锁</li>
 *     <li>processingToken：处理令牌，用于乐观锁</li>
 * </ul>
 *
 * @see KnowledgeChunk 文档分块实体
 * @see com.hqc.smrag.enums.DocumentStatus 文档状态枚举
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocument {

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文档名称
     */
    private String name;

    /**
     * 文件存储路径
     */
    private String filePath;

    private String storageType;

    private String objectKey;

    private String parsedTextObjectKey;

    private Integer aclVersion;

    /**
     * 文件类型（PDF、WORD、TXT）
     */
    private String fileType;

    /**
     * 处理状态
     * <p>
     * 可能的值：PENDING、PROCESSING、COMPLETED、FAILED、DELETING
     */
    private String status;

    /**
     * 文档切块策略：TOKEN 或 RECURSIVE。
     */
    private String chunkingStrategy;

    /**
     * 分块数量
     */
    private Integer chunkCount;

    /**
     * 知识库ID
     */
    private Long knowledgeBaseId;

    /**
     * 文件校验和（SHA-256）
     */
    private String checksum;

    /**
     * 版本号
     * <p>
     * 用于乐观锁，每次重新处理时递增。
     */
    private Integer version;

    /**
     * 失败原因
     * <p>
     * 当处理失败时，记录失败原因。
     */
    private String failureReason;

    /**
     * 处理令牌
     * <p>
     * 用于乐观锁，每次处理时生成新的 UUID。
     */
    private UUID processingToken;

    /**
     * 处理开始时间
     */
    private LocalDateTime processingStartedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
