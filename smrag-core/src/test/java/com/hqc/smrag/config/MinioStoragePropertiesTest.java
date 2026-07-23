package com.hqc.smrag.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MinioStoragePropertiesTest {

    @Test
    void bindsDocumentStorageSettings() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
            "smrag.storage.minio.endpoint", "http://127.0.0.1:9000",
            "smrag.storage.minio.access-key", "minioadmin",
            "smrag.storage.minio.secret-key", "minioadmin",
            "smrag.storage.minio.bucket-name", "super-agent-document",
            "smrag.storage.minio.object-prefix", "rag/document",
            "smrag.storage.minio.parsed-text-prefix", "rag/parsed-text"
        ));

        MinioStorageProperties properties = new Binder(source)
            .bind("smrag.storage.minio", MinioStorageProperties.class)
            .orElseThrow(AssertionError::new);

        assertThat(properties.getEndpoint()).isEqualTo("http://127.0.0.1:9000");
        assertThat(properties.getAccessKey()).isEqualTo("minioadmin");
        assertThat(properties.getSecretKey()).isEqualTo("minioadmin");
        assertThat(properties.getBucketName()).isEqualTo("super-agent-document");
        assertThat(properties.getObjectPrefix()).isEqualTo("rag/document");
        assertThat(properties.getParsedTextPrefix()).isEqualTo("rag/parsed-text");
    }
}
