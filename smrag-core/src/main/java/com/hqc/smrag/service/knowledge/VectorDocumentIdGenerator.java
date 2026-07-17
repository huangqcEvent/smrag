package com.hqc.smrag.service.knowledge;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class VectorDocumentIdGenerator {

    public UUID generate(String knowledgeBaseId, long documentId, int version, int chunkIndex) {
        String key = knowledgeBaseId + "/" + documentId + "/" + version + "/" + chunkIndex;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
