package com.hqc.smrag.service.knowledge;

import cn.hutool.json.JSONUtil;
import com.hqc.smrag.dto.DocumentParseTaskDTO;
import com.hqc.smrag.entity.DocumentIngestionOutbox;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.mapper.DocumentIngestionOutboxMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文档上传持久化服务
 * <p>
 * 负责文档上传时的数据库持久化操作，采用 Outbox 模式确保数据一致性。
 * <p>
 * Outbox 模式流程：
 * <ol>
 *     <li>在同一事务中创建文档记录和 Outbox 记录</li>
 *     <li>Kafka 消费者从 Outbox 表中读取待处理任务</li>
 *     <li>处理完成后删除 Outbox 记录</li>
 * </ol>
 * <p>
 * 这种模式确保了：
 * <ul>
 *     <li>文档记录和消息发送的一致性</li>
 *     <li>消息不会丢失</li>
 *     <li>支持重试和错误处理</li>
 * </ul>
 *
 * @see DocumentIngestionOutbox Outbox 实体
 * @see DocumentParseTaskDTO 文档解析任务 DTO
 */
@Service
@RequiredArgsConstructor
public class DocumentUploadPersistenceService {

    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentIngestionOutboxMapper outboxMapper;

    /**
     * 创建文档记录和 Outbox 记录
     * <p>
     * 在同一事务中完成：
     * <ol>
     *     <li>插入文档记录到 knowledge_document 表</li>
     *     <li>创建文档解析任务 DTO</li>
     *     <li>插入 Outbox 记录到 document_ingestion_outbox 表</li>
     * </ol>
     *
     * @param document 知识库文档实体
     * @return 文档ID
     */
    @Transactional
    public Long createDocumentAndOutbox(KnowledgeDocument document) {
        documentMapper.insert(document);
        DocumentParseTaskDTO task = new DocumentParseTaskDTO(
            document.getId(),
            document.getFilePath(),
            document.getFileType(),
            document.getKnowledgeBaseId(),
            document.getVersion(),
            document.getChecksum(),
            document.getProcessingToken()
        );
        LocalDateTime now = LocalDateTime.now();
        DocumentIngestionOutbox outbox = new DocumentIngestionOutbox();
        outbox.setId(UUID.randomUUID());
        outbox.setDocumentId(document.getId());
        outbox.setPayload(JSONUtil.toJsonStr(task));
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(now);
        outbox.setCreatedAt(now);
        outboxMapper.insert(outbox);
        return document.getId();
    }
}
