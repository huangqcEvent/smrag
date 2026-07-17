package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.entity.KnowledgeChunk;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.enums.DocumentStatus;
import com.hqc.smrag.mapper.KnowledgeChunkMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 向量重建索引运行器
 * <p>
 * 应用启动时，将数据库中的已处理文档重新索引到向量数据库。
 * <p>
 * 触发条件：配置 smrag.ai.rag.reindex-on-startup=true
 * <p>
 * 执行流程：
 * <ol>
 *     <li>查询所有状态为 COMPLETED 的文档</li>
 *     <li>查询对应的分块记录</li>
 *     <li>将分块转换为向量文档格式</li>
 *     <li>批量写入向量数据库</li>
 *     <li>更新分块记录的向量ID</li>
 * </ol>
 * <p>
 * 错误处理：
 * <ul>
 *     <li>单文档失败不影响其他文档</li>
 *     <li>失败时尝试回滚已写入的向量</li>
 *     <li>记录失败文档数量和错误日志</li>
 * </ul>
 *
 * @see VectorStore 向量数据库
 * @see VectorDocumentIdGenerator 向量ID生成器
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smrag.ai.rag.reindex-on-startup", havingValue = "true")
public class VectorReindexRunner implements ApplicationRunner {

    /**
     * 批量更新大小
     */
    private static final int BATCH_SIZE = 500;

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final VectorStore vectorStore;
    private final VectorDocumentIdGenerator idGenerator;

    /**
     * 执行重建索引
     * <p>
     * 在应用启动完成后自动执行。
     */
    @Override
    public void run(ApplicationArguments args) {
        Map<Long, KnowledgeDocument> documents = documentMapper.selectList(null).stream()
            .filter(document -> DocumentStatus.COMPLETED.name().equals(document.getStatus()))
            .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
        Map<Long, List<KnowledgeChunk>> chunksByDocument = chunkMapper.selectList(null).stream()
            .filter(chunk -> chunk.getDocumentId() != null)
            .collect(Collectors.groupingBy(KnowledgeChunk::getDocumentId));

        int successfulDocuments = 0;
        int successfulChunks = 0;
        int failedDocuments = 0;
        for (Map.Entry<Long, List<KnowledgeChunk>> entry : chunksByDocument.entrySet()) {
            KnowledgeDocument document = documents.get(entry.getKey());
            if (document == null) {
                continue;
            }
            int currentVersion = document.getVersion() == null ? 1 : document.getVersion();
            List<KnowledgeChunk> currentChunks = entry.getValue().stream()
                .filter(chunk -> chunk.getDocumentVersion() == null
                    || chunk.getDocumentVersion() == currentVersion)
                .toList();
            if (currentChunks.isEmpty()) {
                continue;
            }
            List<Document> vectors = List.of();
            try {
                vectors = toVectorDocuments(document, currentChunks);
                vectorStore.add(vectors);
                chunkMapper.updateById(currentChunks, BATCH_SIZE);
                successfulDocuments++;
                successfulChunks += vectors.size();
            } catch (RuntimeException failure) {
                failedDocuments++;
                if (!vectors.isEmpty()) {
                    try {
                        vectorStore.delete(vectors.stream().map(Document::getId).toList());
                    } catch (RuntimeException compensationFailure) {
                        failure.addSuppressed(compensationFailure);
                    }
                }
                log.error("Unable to reindex document {}", document.getId(), failure);
            }
        }
        log.info(
            "Vector reindex completed: {} documents, {} chunks, {} failures",
            successfulDocuments,
            successfulChunks,
            failedDocuments
        );
    }

    /**
     * 将分块实体转换为向量文档
     * <p>
     * 转换过程：
     * <ol>
     *     <li>生成向量ID</li>
     *     <li>构建元数据（知识库ID、文档ID、页码等）</li>
     *     <li>创建 Spring AI Document 对象</li>
     * </ol>
     *
     * @param document 文档实体
     * @param chunks   分块实体列表
     * @return 向量文档列表
     */
    private List<Document> toVectorDocuments(
        KnowledgeDocument document,
        List<KnowledgeChunk> chunks
    ) {
        int version = document.getVersion() == null ? 1 : document.getVersion();
        String knowledgeBaseId = Objects.requireNonNullElse(document.getKnowledgeBaseId(), "default");
        List<Document> vectors = new ArrayList<>(chunks.size());
        for (int position = 0; position < chunks.size(); position++) {
            KnowledgeChunk chunk = chunks.get(position);
            int chunkIndex = chunk.getChunkIndex() == null ? position : chunk.getChunkIndex();
            UUID vectorId = idGenerator.generate(
                knowledgeBaseId,
                document.getId(),
                version,
                chunkIndex
            );
            chunk.setVectorId(vectorId);
            chunk.setDocumentVersion(version);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("knowledgeBaseId", knowledgeBaseId);
            metadata.put("documentId", document.getId());
            metadata.put("documentVersion", version);
            metadata.put("documentStatus", DocumentStatus.COMPLETED.name());
            metadata.put("chunkIndex", chunkIndex);
            putIfNotNull(metadata, "documentName", document.getName());
            putIfNotNull(metadata, "documentType", document.getFileType());
            putIfNotNull(metadata, "checksum", document.getChecksum());
            if (chunk.getPageNumber() != null) {
                metadata.put("pageNumber", chunk.getPageNumber());
            }
            vectors.add(new Document(vectorId.toString(), chunk.getContent(), metadata));
        }
        return vectors;
    }

    /**
     * 如果值不为 null，则添加到元数据中
     */
    private void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}
