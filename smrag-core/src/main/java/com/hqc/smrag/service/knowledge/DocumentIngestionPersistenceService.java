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

@Service
@RequiredArgsConstructor
public class DocumentIngestionPersistenceService {

    private static final int BATCH_SIZE = 500;

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;

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

    @Transactional
    public void saveCompleted(
        long documentId,
        int version,
        UUID processingToken,
        List<Document> chunks
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
                .set(KnowledgeDocument::getFailureReason, null)
                .set(KnowledgeDocument::getProcessingStartedAt, null)
                .set(KnowledgeDocument::getUpdateTime, LocalDateTime.now())
        );
        requireMatchingVersion(updated, documentId, version);
    }

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

    private Integer number(Object value, Integer defaultValue) {
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private void requireMatchingVersion(int updated, long documentId, int version) {
        if (updated != 1) {
            throw new OptimisticLockingFailureException(
                "Document " + documentId + " version " + version + " is no longer current"
            );
        }
    }
}
