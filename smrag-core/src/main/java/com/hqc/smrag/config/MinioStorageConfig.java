package com.hqc.smrag.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MinioStorageConfig {

    private final MinioStorageProperties properties;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
            .endpoint(properties.getEndpoint())
            .credentials(properties.getAccessKey(), properties.getSecretKey())
            .build();
    }

    @Bean
    public ApplicationRunner initializeDocumentBucket(MinioClient client) {
        return ignored -> {
            String bucket = properties.getBucketName();
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        };
    }
}
