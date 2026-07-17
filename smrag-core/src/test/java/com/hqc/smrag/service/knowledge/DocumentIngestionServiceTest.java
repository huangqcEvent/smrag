package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.dto.DocumentParseTaskDTO;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.enums.DocumentStatus;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentIngestionServiceTest {

    private static final UUID PROCESSING_TOKEN =
        UUID.fromString("00000000-0000-0000-0000-000000000042");

    private final KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
    private final SpringAiDocumentReader reader = mock(SpringAiDocumentReader.class);
    private final DocumentChunker chunker = mock(DocumentChunker.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final DocumentIngestionPersistenceService persistenceService =
        mock(DocumentIngestionPersistenceService.class);
    private DocumentIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new DocumentIngestionService(
            documentMapper,
            reader,
            chunker,
            vectorStore,
            persistenceService
        );
    }

    @Test
    void addsVectorsOnceAndCompletesRelationalPersistence() {
        KnowledgeDocument document = document(2, DocumentStatus.PENDING);
        DocumentParseTaskDTO task = task(2);
        List<Document> sources = List.of(new Document("source text"));
        List<Document> chunks = chunks();
        when(documentMapper.selectById(42L)).thenReturn(document);
        when(persistenceService.markProcessing(42L, 2, PROCESSING_TOKEN)).thenReturn(true);
        when(reader.read(Path.of(task.getFilePath()), "PDF")).thenReturn(sources);
        when(chunker.chunk(
            sources,
            "default",
            42L,
            "enterprise-rag.pdf",
            "PDF",
            2,
            DocumentStatus.COMPLETED.name(),
            "checksum-42"
        )).thenReturn(chunks);

        ingestionService.process(task);

        verify(persistenceService).markProcessing(42L, 2, PROCESSING_TOKEN);
        verify(vectorStore).add(chunks);
        verify(persistenceService).saveCompleted(42L, 2, PROCESSING_TOKEN, chunks);
    }

    @Test
    void compensatesVectorsAndRecordsFailureWhenRelationalPersistenceFails() {
        KnowledgeDocument document = document(2, DocumentStatus.PENDING);
        DocumentParseTaskDTO task = task(2);
        List<Document> sources = List.of(new Document("source text"));
        List<Document> chunks = chunks();
        IllegalStateException failure = new IllegalStateException("relational commit failed");
        when(documentMapper.selectById(42L)).thenReturn(document);
        when(persistenceService.markProcessing(42L, 2, PROCESSING_TOKEN)).thenReturn(true);
        when(reader.read(Path.of(task.getFilePath()), "PDF")).thenReturn(sources);
        when(chunker.chunk(
            sources,
            "default",
            42L,
            "enterprise-rag.pdf",
            "PDF",
            2,
            DocumentStatus.COMPLETED.name(),
            "checksum-42"
        )).thenReturn(chunks);
        doThrow(failure).when(persistenceService)
            .saveCompleted(42L, 2, PROCESSING_TOKEN, chunks);

        assertThatThrownBy(() -> ingestionService.process(task)).isSameAs(failure);

        verify(vectorStore).delete(chunks.stream().map(Document::getId).toList());
        verify(persistenceService).markFailedInNewTransaction(
            eq(42L),
            eq(2),
            eq(PROCESSING_TOKEN),
            contains("relational commit failed")
        );
    }

    @Test
    void ignoresStaleDocumentVersion() {
        when(documentMapper.selectById(42L)).thenReturn(document(3, DocumentStatus.PENDING));

        ingestionService.process(task(2));

        verify(reader, never()).read(eq(Path.of("uploads/enterprise-rag.pdf")), eq("PDF"));
        verify(vectorStore, never()).add(anyList());
        verify(persistenceService, never()).markProcessing(42L, 2, PROCESSING_TOKEN);
    }

    @Test
    void ignoresDuplicateTaskWhenAnotherConsumerAlreadyOwnsProcessing() {
        when(documentMapper.selectById(42L)).thenReturn(document(2, DocumentStatus.PROCESSING));
        when(persistenceService.markProcessing(42L, 2, PROCESSING_TOKEN)).thenReturn(false);

        ingestionService.process(task(2));

        verify(reader, never()).read(eq(Path.of("uploads/enterprise-rag.pdf")), eq("PDF"));
        verify(vectorStore, never()).add(anyList());
        verify(persistenceService, never()).saveCompleted(
            eq(42L),
            eq(2),
            eq(PROCESSING_TOKEN),
            anyList()
        );
    }

    private KnowledgeDocument document(int version, DocumentStatus status) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(42L);
        document.setKnowledgeBaseId("default");
        document.setName("enterprise-rag.pdf");
        document.setFilePath("uploads/enterprise-rag.pdf");
        document.setFileType("PDF");
        document.setVersion(version);
        document.setChecksum("checksum-42");
        document.setProcessingToken(PROCESSING_TOKEN);
        document.setStatus(status.name());
        return document;
    }

    private DocumentParseTaskDTO task(int version) {
        return new DocumentParseTaskDTO(
            42L,
            "uploads/enterprise-rag.pdf",
            "PDF",
            "default",
            version,
            "checksum-42",
            PROCESSING_TOKEN
        );
    }

    private List<Document> chunks() {
        return List.of(
            new Document(UUID.randomUUID().toString(), "chunk one", java.util.Map.of("chunkIndex", 0)),
            new Document(UUID.randomUUID().toString(), "chunk two", java.util.Map.of("chunkIndex", 1))
        );
    }
}
