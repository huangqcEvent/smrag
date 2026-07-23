package com.hqc.smrag.service.storage;

import com.hqc.smrag.entity.KnowledgeDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentSourceResolverTest {

    @TempDir
    Path tempDir;

    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final DocumentSourceResolver resolver = new DocumentSourceResolver(storage);

    @Test
    void keepsHistoricalLocalFileAfterUse() throws Exception {
        Path local = Files.writeString(tempDir.resolve("legacy.txt"), "legacy");
        KnowledgeDocument document = new KnowledgeDocument();
        document.setStorageType("LOCAL");
        document.setFilePath(local.toString());
        document.setFileType("TXT");

        try (DocumentSourceResolver.ResolvedSource source = resolver.resolve(document)) {
            assertThat(source.path()).isEqualTo(local);
        }

        assertThat(local).exists();
    }

    @Test
    void deletesMaterializedMinioFileAfterUse() throws Exception {
        Path materialized = Files.writeString(tempDir.resolve("object.pdf"), "pdf");
        when(storage.materialize("rag/document/42/object.pdf", ".pdf"))
            .thenReturn(materialized);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setStorageType("MINIO");
        document.setObjectKey("rag/document/42/object.pdf");
        document.setFileType("PDF");

        try (DocumentSourceResolver.ResolvedSource source = resolver.resolve(document)) {
            assertThat(source.path()).isEqualTo(materialized);
        }

        assertThat(materialized).doesNotExist();
    }
}
