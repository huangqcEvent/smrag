package com.hqc.smrag.service.storage;

import com.hqc.smrag.config.MinioStorageProperties;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectResponse;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioDocumentStorageTest {

    @TempDir
    Path tempDir;

    private final MinioClient client = mock(MinioClient.class);
    private final MinioStorageProperties properties = properties();

    @Test
    void uploadsToConfiguredPrivateBucket() throws Exception {
        MinioDocumentStorage storage = new MinioDocumentStorage(client, properties, tempDir);

        storage.put(
            "rag/document/42/report.pdf",
            new ByteArrayInputStream("pdf".getBytes()),
            3,
            "application/pdf"
        );

        ArgumentCaptor<PutObjectArgs> args = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(client).putObject(args.capture());
        assertThat(args.getValue().bucket()).isEqualTo("super-agent-document");
        assertThat(args.getValue().object()).isEqualTo("rag/document/42/report.pdf");
        assertThat(args.getValue().contentType()).isEqualTo("application/pdf");
    }

    @Test
    void readsOnlyTheRequestedObjectRange() throws Exception {
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(10L);
        when(stat.contentType()).thenReturn("application/pdf");
        when(client.statObject(any())).thenReturn(stat);
        when(client.getObject(any())).thenReturn(new GetObjectResponse(
            Headers.of(),
            "super-agent-document",
            null,
            "rag/document/42/report.pdf",
            new ByteArrayInputStream("2345".getBytes())
        ));
        MinioDocumentStorage storage = new MinioDocumentStorage(client, properties, tempDir);

        try (StoredObject object = storage.get("rag/document/42/report.pdf", 2L, 4L)) {
            assertThat(object.stream().readAllBytes()).isEqualTo("2345".getBytes());
            assertThat(object.totalSize()).isEqualTo(10);
            assertThat(object.contentLength()).isEqualTo(4);
            assertThat(object.contentType()).isEqualTo("application/pdf");
        }
    }

    @Test
    void removesPartialTemporaryFileWhenDownloadFails() throws Exception {
        when(client.statObject(any())).thenThrow(new IOException("minio unavailable"));
        MinioDocumentStorage storage = new MinioDocumentStorage(client, properties, tempDir);

        assertThatThrownBy(() -> storage.materialize("rag/document/42/report.pdf", ".pdf"))
            .isInstanceOf(IllegalStateException.class);
        try (var files = Files.list(tempDir)) {
            assertThat(files).isEmpty();
        }
    }

    private MinioStorageProperties properties() {
        MinioStorageProperties value = new MinioStorageProperties();
        value.setBucketName("super-agent-document");
        return value;
    }
}
