package com.hqc.smrag.service.storage;

import com.hqc.smrag.config.MinioStorageProperties;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class MinioDocumentStorage implements DocumentStorage {

    private final MinioClient client;
    private final MinioStorageProperties properties;
    private final Path temporaryDirectory;

    @Autowired
    public MinioDocumentStorage(MinioClient client, MinioStorageProperties properties) {
        this(client, properties, Path.of(System.getProperty("java.io.tmpdir")));
    }

    MinioDocumentStorage(
        MinioClient client,
        MinioStorageProperties properties,
        Path temporaryDirectory
    ) {
        this.client = client;
        this.properties = properties;
        this.temporaryDirectory = temporaryDirectory;
    }

    @Override
    public void put(String objectKey, InputStream stream, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                .bucket(properties.getBucketName())
                .object(objectKey)
                .stream(stream, size, -1)
                .contentType(contentType)
                .build());
        } catch (Exception failure) {
            throw storageFailure("upload", objectKey, failure);
        }
    }

    @Override
    public StoredObject get(String objectKey, Long offset, Long length) {
        try {
            StatObjectResponse stat = client.statObject(StatObjectArgs.builder()
                .bucket(properties.getBucketName())
                .object(objectKey)
                .build());
            long start = offset == null ? 0 : offset;
            long requestedLength = length == null ? stat.size() - start : length;
            long contentLength = Math.max(0, Math.min(requestedLength, stat.size() - start));
            GetObjectArgs.Builder builder = GetObjectArgs.builder()
                .bucket(properties.getBucketName())
                .object(objectKey);
            if (offset != null) {
                builder.offset(offset);
            }
            if (length != null) {
                builder.length(length);
            }
            GetObjectResponse response = client.getObject(builder.build());
            return new StoredObject(response, stat.size(), contentLength, stat.contentType());
        } catch (Exception failure) {
            throw storageFailure("read", objectKey, failure);
        }
    }

    @Override
    public Path materialize(String objectKey, String suffix) {
        Path temporaryFile = null;
        try {
            Files.createDirectories(temporaryDirectory);
            temporaryFile = Files.createTempFile(
                temporaryDirectory,
                "smrag-document-",
                suffix == null ? ".tmp" : suffix
            );
            try (StoredObject object = get(objectKey, null, null)) {
                Files.copy(object.stream(), temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return temporaryFile;
        } catch (Exception failure) {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (Exception ignored) {
                    // Preserve the original storage failure.
                }
            }
            if (failure instanceof IllegalStateException stateFailure) {
                throw stateFailure;
            }
            throw storageFailure("materialize", objectKey, failure);
        }
    }

    @Override
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            client.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.getBucketName())
                .object(objectKey)
                .build());
        } catch (Exception failure) {
            throw storageFailure("delete", objectKey, failure);
        }
    }

    private IllegalStateException storageFailure(
        String operation,
        String objectKey,
        Exception failure
    ) {
        return new IllegalStateException(
            "Unable to " + operation + " document object " + objectKey,
            failure
        );
    }
}
