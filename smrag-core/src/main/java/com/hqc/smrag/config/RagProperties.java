package com.hqc.smrag.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "smrag.ai.rag")
public class RagProperties {

    @Min(1)
    @Max(100)
    private int topK = 8;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double similarityThreshold = 0.40;

    @Min(1)
    private int maxContextDocuments = 5;

    @Min(1)
    private int memoryWindow = 12;

    @Min(1)
    private int chunkSize = 800;

    @Min(0)
    private int minChunkSizeChars = 350;

    @Min(0)
    private int minChunkLengthToEmbed = 20;

    @Min(1)
    private int maxNumChunks = 5000;

    private boolean keepSeparator = true;

    @Min(1)
    private long maxUploadSizeBytes = 20L * 1024 * 1024;

    private boolean reindexOnStartup;
}
