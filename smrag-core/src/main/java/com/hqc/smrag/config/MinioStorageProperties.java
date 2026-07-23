package com.hqc.smrag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "smrag.storage.minio")
public class MinioStorageProperties {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private String objectPrefix;
    private String parsedTextPrefix;
}
