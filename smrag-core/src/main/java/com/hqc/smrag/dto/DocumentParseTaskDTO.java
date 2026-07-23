package com.hqc.smrag.dto;

import com.hqc.smrag.service.knowledge.KnowledgeBaseDefaults;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 文档解析任务 DTO
 * <p>
 * 封装 Kafka 消息中的文档解析任务信息，包括：
 * <ul>
 *     <li>documentId：待解析的文档ID</li>
 *     <li>filePath：文档文件路径</li>
 *     <li>fileType：文档类型（PDF、WORD、TXT）</li>
 *     <li>knowledgeBaseId：知识库ID</li>
 *     <li>version：文档版本号</li>
 *     <li>checksum：文件校验和</li>
 *     <li>processingToken：处理令牌，用于乐观锁</li>
 * </ul>
 * <p>
 * 该 DTO 由 {@link com.hqc.smrag.service.knowledge.DocumentUploadPersistenceService} 创建，
 * 通过 Kafka 发送到 {@link com.hqc.smrag.kafka.DocumentParseConsumer} 进行消费。
 *
 * @see com.hqc.smrag.service.knowledge.DocumentIngestionService 文档解析服务
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentParseTaskDTO {

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 文档文件路径
     */
    private String filePath;

    private String objectKey;

    /**
     * 文档类型（PDF、WORD、TXT）
     */
    private String fileType;

    /**
     * 知识库ID
     */
    private String knowledgeBaseId;

    /**
     * 文档版本号
     */
    private Integer version;

    /**
     * 文件校验和（SHA-256）
     */
    private String checksum;

    /**
     * 处理令牌
     * <p>
     * 用于乐观锁，防止重复处理。
     */
    private UUID processingToken;

    /**
     * 简化构造函数
     * <p>
     * 用于测试或简化场景，默认知识库 code 为 "company-main"，版本号为 1。
     *
     * @param documentId 文档ID
     * @param filePath   文件路径
     * @param fileType   文件类型
     */
    public DocumentParseTaskDTO(Long documentId, String filePath, String fileType) {
        this(
            documentId,
            filePath,
            null,
            fileType,
            KnowledgeBaseDefaults.MAIN_CODE,
            1,
            null,
            null
        );
    }

    public DocumentParseTaskDTO(
        Long documentId,
        String filePath,
        String fileType,
        String knowledgeBaseId,
        Integer version,
        String checksum,
        UUID processingToken
    ) {
        this(
            documentId, filePath, null, fileType, knowledgeBaseId,
            version, checksum, processingToken);
    }
}
