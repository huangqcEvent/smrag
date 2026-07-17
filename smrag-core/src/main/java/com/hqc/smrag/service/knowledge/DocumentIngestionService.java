package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.dto.DocumentParseTaskDTO;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.enums.DocumentStatus;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 文档解析服务
 * <p>
 * 负责处理 Kafka 消息中的文档解析任务，将文档转换为向量并存储。
 * 处理流程：
 * <ol>
 *     <li>校验任务有效性（文档存在、版本匹配、状态正确）</li>
 *     <li>标记文档为处理中状态</li>
 *     <li>读取文档内容</li>
 *     <li>将文档分块</li>
 *     <li>将分块写入向量数据库</li>
 *     <li>保存分块记录到数据库</li>
 *     <li>更新文档状态为已完成</li>
 * </ol>
 * <p>
 * 错误处理：
 * <ul>
 *     <li>向量写入失败时，尝试回滚已写入的向量</li>
 *     <li>处理失败时，更新文档状态为失败并记录原因</li>
 * </ul>
 *
 * @see DocumentParseTaskDTO 文档解析任务 DTO
 * @see SpringAiDocumentReader 文档阅读器
 * @see DocumentChunker 文档分块器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    /**
     * 失败原因最大长度
     */
    private static final int MAX_FAILURE_REASON_LENGTH = 2000;

    private final KnowledgeDocumentMapper documentMapper;
    private final SpringAiDocumentReader reader;
    private final DocumentChunker chunker;
    private final VectorStore vectorStore;
    private final DocumentIngestionPersistenceService persistenceService;

    /**
     * 处理文档解析任务
     * <p>
     * 处理流程：
     * <ol>
     *     <li>校验任务有效性</li>
     *     <li>标记处理中状态</li>
     *     <li>读取并分块文档</li>
     *     <li>写入向量数据库</li>
     *     <li>保存分块记录</li>
     * </ol>
     *
     * @param task 文档解析任务
     */
    public void process(DocumentParseTaskDTO task) {
        KnowledgeDocument document = documentMapper.selectById(task.getDocumentId());
        if (document == null) {
            log.info("Ignoring ingestion task for deleted document {}", task.getDocumentId());
            return;
        }
        if (!isCurrentTask(document, task)) {
            log.info(
                "Ignoring stale ingestion task for document {}, task version {}, current version {}",
                task.getDocumentId(),
                task.getVersion(),
                document.getVersion()
            );
            return;
        }
        if (DocumentStatus.COMPLETED.name().equals(document.getStatus())) {
            log.info("Document {} version {} is already indexed", document.getId(), document.getVersion());
            return;
        }

        List<Document> chunks = List.of();
        boolean vectorWriteAttempted = false;
        try {
            if (!persistenceService.markProcessing(
                document.getId(),
                document.getVersion(),
                document.getProcessingToken()
            )) {
                log.info(
                    "Document {} version {} is already being processed or deleted",
                    document.getId(),
                    document.getVersion()
                );
                return;
            }
            List<Document> sources = reader.read(
                Path.of(document.getFilePath()),
                document.getFileType()
            );
            chunks = chunker.chunk(
                sources,
                document.getKnowledgeBaseId(),
                document.getId(),
                document.getName(),
                document.getFileType(),
                document.getVersion(),
                DocumentStatus.COMPLETED.name(),
                document.getChecksum()
            );
            vectorWriteAttempted = true;
            vectorStore.add(chunks);
            persistenceService.saveCompleted(
                document.getId(),
                document.getVersion(),
                document.getProcessingToken(),
                chunks
            );
            log.info(
                "Indexed document {} version {} with {} chunks",
                document.getId(),
                document.getVersion(),
                chunks.size()
            );
        } catch (RuntimeException failure) {
            if (vectorWriteAttempted && !chunks.isEmpty()) {
                try {
                    vectorStore.delete(chunks.stream().map(Document::getId).toList());
                } catch (RuntimeException compensationFailure) {
                    failure.addSuppressed(compensationFailure);
                }
            }
            try {
                persistenceService.markFailedInNewTransaction(
                    document.getId(),
                    document.getVersion(),
                    document.getProcessingToken(),
                    failureReason(failure)
                );
            } catch (RuntimeException statusFailure) {
                failure.addSuppressed(statusFailure);
            }
            throw failure;
        }
    }

    /**
     * 判断任务是否为当前任务
     * <p>
     * 比较文档的版本、校验和、知识库ID、处理令牌是否与任务一致。
     */
    private boolean isCurrentTask(KnowledgeDocument document, DocumentParseTaskDTO task) {
        return Objects.equals(document.getVersion(), task.getVersion())
            && Objects.equals(document.getChecksum(), task.getChecksum())
            && Objects.equals(document.getKnowledgeBaseId(), task.getKnowledgeBaseId())
            && Objects.equals(document.getProcessingToken(), task.getProcessingToken());
    }

    /**
     * 提取失败原因
     * <p>
     * 从异常中提取根本原因，并截断到指定长度。
     *
     * @param failure 异常
     * @return 失败原因字符串
     */
    public static String failureReason(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        String reason = root.getClass().getSimpleName()
            + (message == null || message.isBlank() ? "" : ": " + message);
        return reason.length() <= MAX_FAILURE_REASON_LENGTH
            ? reason
            : reason.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
