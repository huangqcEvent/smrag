package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.dto.DocumentParseTaskDTO;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.enums.DocumentStatus;
import com.hqc.smrag.enums.ChunkingStrategy;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.mapper.DocPermissionMapper;
import com.hqc.smrag.service.DocPermissionService;
import com.hqc.smrag.service.storage.DocumentSourceResolver;
import com.hqc.smrag.service.storage.DocumentStorage;
import com.hqc.smrag.config.MinioStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
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
    private final DocumentSourceResolver sourceResolver = mock(DocumentSourceResolver.class);
    private final DocPermissionMapper permissionMapper = mock(DocPermissionMapper.class);
    private final DocPermissionService permissionService = mock(DocPermissionService.class);
    private final DocumentStorage documentStorage = mock(DocumentStorage.class);
    @TempDir
    Path tempDir;
    private DocumentIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        when(sourceResolver.resolve(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            return new DocumentSourceResolver.ResolvedSource(
                Path.of(document.getFilePath()), false);
        });
        when(permissionMapper.selectReadableRoleIdsByDocumentId(42L))
            .thenReturn(List.of(1L, 2L));
        MinioStorageProperties storageProperties = new MinioStorageProperties();
        storageProperties.setParsedTextPrefix("rag/parsed-text");
        ingestionService = new DocumentIngestionService(
            documentMapper,
            reader,
            chunker,
            vectorStore,
            persistenceService,
            sourceResolver,
            permissionMapper,
            permissionService,
            new VectorBatchWriter(vectorStore),
            documentStorage,
            storageProperties
        );
    }

    @Test
    void resolvesMinioSourceAndDeletesTemporaryFileAfterIngestion() throws Exception {
        KnowledgeDocument document = document(2, DocumentStatus.PENDING);
        document.setStorageType("MINIO");
        document.setObjectKey("rag/document/42/file.pdf");
        document.setFilePath(null);
        Path temporaryFile = Files.writeString(tempDir.resolve("source.pdf"), "content");
        when(documentMapper.selectById(42L)).thenReturn(document);
        when(persistenceService.markProcessing(42L, 2, PROCESSING_TOKEN)).thenReturn(true);
        doReturn(new DocumentSourceResolver.ResolvedSource(temporaryFile, true))
            .when(sourceResolver).resolve(document);
        List<Document> sources = List.of(new Document("source text"));
        List<Document> chunks = chunks();
        when(reader.read(temporaryFile, "PDF")).thenReturn(sources);
        when(chunker.chunk(
            sources, ChunkingStrategy.TOKEN,
            1L, 42L, "enterprise-rag.pdf", "PDF", 2,
            DocumentStatus.COMPLETED.name(), "checksum-42", 1,
            java.util.Set.of(1L, 2L))).thenReturn(chunks);

        ingestionService.process(task(2));

        verify(reader).read(temporaryFile, "PDF");
        verify(documentStorage).put(
            eq("rag/parsed-text/42/v2.txt"),
            any(java.io.InputStream.class),
            eq(11L),
            eq("text/plain;charset=UTF-8")
        );
        verify(persistenceService).saveCompleted(
            42L,
            2,
            PROCESSING_TOKEN,
            chunks,
            "rag/parsed-text/42/v2.txt"
        );
        org.assertj.core.api.Assertions.assertThat(temporaryFile).doesNotExist();
    }

    @Test
    void addsVectorsOnceAndCompletesRelationalPersistence() {
        KnowledgeDocument document = document(2, DocumentStatus.PENDING);
        document.setChunkingStrategy("RECURSIVE");
        DocumentParseTaskDTO task = task(2);
        List<Document> sources = List.of(new Document("source text"));
        List<Document> chunks = chunks();
        when(documentMapper.selectById(42L)).thenReturn(document);
        when(persistenceService.markProcessing(42L, 2, PROCESSING_TOKEN)).thenReturn(true);
        when(reader.read(Path.of(task.getFilePath()), "PDF")).thenReturn(sources);
        when(chunker.chunk(
            sources,
            ChunkingStrategy.RECURSIVE,
            1L,
            42L,
            "enterprise-rag.pdf",
            "PDF",
            2,
            DocumentStatus.COMPLETED.name(),
            "checksum-42",
            1,
            java.util.Set.of(1L, 2L)
        )).thenReturn(chunks);

        ingestionService.process(task);

        verify(persistenceService).markProcessing(42L, 2, PROCESSING_TOKEN);
        InOrder order = inOrder(vectorStore, permissionService, persistenceService);
        order.verify(vectorStore).add(chunks);
        order.verify(permissionService).synchronizeVectorAcl(42L);
        order.verify(persistenceService).saveCompleted(
            42L,
            2,
            PROCESSING_TOKEN,
            chunks,
            null
        );
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
            ChunkingStrategy.TOKEN,
            1L,
            42L,
            "enterprise-rag.pdf",
            "PDF",
            2,
            DocumentStatus.COMPLETED.name(),
            "checksum-42",
            1,
            java.util.Set.of(1L, 2L)
        )).thenReturn(chunks);
        doThrow(failure).when(persistenceService)
            .saveCompleted(42L, 2, PROCESSING_TOKEN, chunks, null);

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
            anyList(),
            org.mockito.ArgumentMatchers.nullable(String.class)
        );
    }

    @Test
    void rejectsUnknownPersistedStrategyAndRecordsFailure() {
        KnowledgeDocument document = document(2, DocumentStatus.PENDING);
        document.setChunkingStrategy("UNKNOWN");
        when(documentMapper.selectById(42L)).thenReturn(document);
        when(persistenceService.markProcessing(42L, 2, PROCESSING_TOKEN))
            .thenReturn(true);
        when(reader.read(Path.of("uploads/enterprise-rag.pdf"), "PDF"))
            .thenReturn(List.of(new Document("source text")));

        assertThatThrownBy(() -> ingestionService.process(task(2)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("UNKNOWN");

        verify(chunker, never()).chunk(
            anyList(),
            any(ChunkingStrategy.class),
            any(),
            any(Long.class),
            any(),
            any(),
            any(Integer.class),
            any(),
            any(),
            any(Integer.class),
            any()
        );
        verify(persistenceService).markFailedInNewTransaction(
            eq(42L),
            eq(2),
            eq(PROCESSING_TOKEN),
            contains("UNKNOWN")
        );
    }

    private KnowledgeDocument document(int version, DocumentStatus status) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(42L);
        document.setKnowledgeBaseId(1L);
        document.setName("enterprise-rag.pdf");
        document.setFilePath("uploads/enterprise-rag.pdf");
        document.setFileType("PDF");
        document.setVersion(version);
        document.setChecksum("checksum-42");
        document.setProcessingToken(PROCESSING_TOKEN);
        document.setStatus(status.name());
        document.setAclVersion(1);
        document.setChunkingStrategy("TOKEN");
        return document;
    }

    private DocumentParseTaskDTO task(int version) {
        return new DocumentParseTaskDTO(
            42L,
            "uploads/enterprise-rag.pdf",
            "PDF",
            1L,
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
