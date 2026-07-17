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

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private static final int MAX_FAILURE_REASON_LENGTH = 2000;

    private final KnowledgeDocumentMapper documentMapper;
    private final SpringAiDocumentReader reader;
    private final DocumentChunker chunker;
    private final VectorStore vectorStore;
    private final DocumentIngestionPersistenceService persistenceService;

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

    private boolean isCurrentTask(KnowledgeDocument document, DocumentParseTaskDTO task) {
        return Objects.equals(document.getVersion(), task.getVersion())
            && Objects.equals(document.getChecksum(), task.getChecksum())
            && Objects.equals(document.getKnowledgeBaseId(), task.getKnowledgeBaseId())
            && Objects.equals(document.getProcessingToken(), task.getProcessingToken());
    }

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
