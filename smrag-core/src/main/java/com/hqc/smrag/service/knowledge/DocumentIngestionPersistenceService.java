package com.hqc.smrag.service.knowledge;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hqc.smrag.entity.KnowledgeChunk;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.enums.DocumentStatus;
import com.hqc.smrag.mapper.KnowledgeChunkMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档解析持久化服务
 * <p>
 * 负责文档解析过程中的数据库状态更新操作，包括：
 * <ul>
 *     <li>标记处理中状态：乐观锁更新文档状态为 PROCESSING</li>
 *     <li>保存解析结果：保存分块记录并更新文档状态为 COMPLETED</li>
 *     <li>标记失败状态：在独立事务中更新文档状态为 FAILED</li>
 * </ul>
 * <p>
 * 并发控制：
 * <ul>
 *     <li>使用版本号和 processingToken 实现乐观锁</li>
 *     <li>失败状态更新使用 REQUIRES_NEW 传播，确保状态能够保存</li>
 * </ul>
 *
 * @see KnowledgeDocument 知识库文档实体
 * @see KnowledgeChunk 知识库分块实体
 */
@Service
@RequiredArgsConstructor
public class DocumentIngestionPersistenceService {

    /**
     * 批量插入大小
     */
    private static final int BATCH_SIZE = 500;

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;

    /**
     * 标记文档为处理中状态
     * <p>
     * 使用乐观锁更新文档状态，条件包括：
     * <ul>
     *     <li>文档ID匹配</li>
     *     <li>版本号匹配</li>
     *     <li>处理令牌匹配</li>
     *     <li>当前状态为 PENDING 或 FAILED</li>
     * </ul>
     *
     * @param documentId      文档ID
     * @param version         版本号
     * @param processingToken 处理令牌
     * @return 是否成功标记（true=成功，false=已被其他进程处理）
     */
    @Transactional
    public boolean markProcessing(long documentId, int version, UUID processingToken) {
        int updated = documentMapper.update(
            null,
            Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getVersion, version)
                .eq(KnowledgeDocument::getProcessingToken, processingToken)
                .in(
                    KnowledgeDocument::getStatus,
                    DocumentStatus.PENDING.name(),
                    DocumentStatus.FAILED.name()
                )
                .set(KnowledgeDocument::getStatus, DocumentStatus.PROCESSING.name())
                .set(KnowledgeDocument::getFailureReason, null)
                .set(KnowledgeDocument::getProcessingStartedAt, LocalDateTime.now())
                .set(KnowledgeDocument::getUpdateTime, LocalDateTime.now())
        );
        return updated == 1;
    }

    /**
     * 保存解析完成结果
     * <p>
     * 在同一事务中完成：
     * <ol>
     *     <li>删除旧的分块记录（如果存在）</li>
     *     <li>插入新的分块记录</li>
     *     <li>更新文档状态为 COMPLETED</li>
     * </ol>
     *
     * @param documentId      文档ID
     * @param version         版本号
     * @param processingToken 处理令牌
     * @param chunks          分块列表
     * @throws OptimisticLockingFailureException 文档版本已更新时抛出
     */
    @Transactional
    public void saveCompleted(
        long documentId,
        int version,
        UUID processingToken,
        List<Document> chunks,
        String parsedTextObjectKey
    ) {
        chunkMapper.delete(
            Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getDocumentId, documentId)
        );

        List<KnowledgeChunk> entities = toEntities(documentId, version, chunks);
        if (!entities.isEmpty()) {
            chunkMapper.insert(entities, BATCH_SIZE);
        }

        int updated = documentMapper.update(
            null,
            Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getVersion, version)
                .eq(KnowledgeDocument::getProcessingToken, processingToken)
                .eq(KnowledgeDocument::getStatus, DocumentStatus.PROCESSING.name())
                .set(KnowledgeDocument::getStatus, DocumentStatus.COMPLETED.name())
                .set(KnowledgeDocument::getChunkCount, entities.size())
                .set(
                    KnowledgeDocument::getParsedTextObjectKey,
                    parsedTextObjectKey
                )
                .set(KnowledgeDocument::getFailureReason, null)
                .set(KnowledgeDocument::getProcessingStartedAt, null)
                .set(KnowledgeDocument::getUpdateTime, LocalDateTime.now())
        );
        requireMatchingVersion(updated, documentId, version);
    }

    /**
     * 在新事务中标记失败状态
     * <p>
     * 使用 REQUIRES_NEW 传播，确保即使外层事务回滚，失败状态也能保存。
     *
     * @param documentId      文档ID
     * @param version         版本号
     * @param processingToken 处理令牌
     * @param failureReason   失败原因
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedInNewTransaction(
        long documentId,
        int version,
        UUID processingToken,
        String failureReason
    ) {
        documentMapper.update(
            null,
            Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getVersion, version)
                .eq(KnowledgeDocument::getProcessingToken, processingToken)
                .eq(KnowledgeDocument::getStatus, DocumentStatus.PROCESSING.name())
                .set(KnowledgeDocument::getStatus, DocumentStatus.FAILED.name())
                .set(KnowledgeDocument::getFailureReason, failureReason)
                .set(KnowledgeDocument::getProcessingStartedAt, null)
                .set(KnowledgeDocument::getUpdateTime, LocalDateTime.now())
        );
    }

    /**
     * 将 Spring AI Document 转换为 KnowledgeChunk 实体列表
     */
    private List<KnowledgeChunk> toEntities(
        long documentId,
        int version,
        List<Document> chunks
    ) {
        List<KnowledgeChunk> entities = new ArrayList<>(chunks.size());
        LocalDateTime createdAt = LocalDateTime.now();
        for (int index = 0; index < chunks.size(); index++) {
            Document document = chunks.get(index);
            Map<String, Object> metadata = document.getMetadata();
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocumentId(documentId);
            chunk.setDocumentVersion(version);
            chunk.setVectorId(UUID.fromString(document.getId()));
            chunk.setContent(document.getText());
            chunk.setChunkIndex(number(metadata.get("chunkIndex"), index));
            chunk.setPageNumber(number(metadata.get("pageNumber"), null));
            chunk.setCreateTime(createdAt);
            entities.add(chunk);
        }
        return entities;
    }

    /**
     * 从元数据中提取数字值
     */
    private Integer number(Object value, Integer defaultValue) {
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    /**
     * 校验乐观锁版本是否匹配
     */
    private void requireMatchingVersion(int updated, long documentId, int version) {
        if (updated != 1) {
            throw new OptimisticLockingFailureException(
                "Document " + documentId + " version " + version + " is no longer current"
            );
        }
    }
}
