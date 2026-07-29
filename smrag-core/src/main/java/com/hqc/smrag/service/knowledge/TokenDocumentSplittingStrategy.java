package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.enums.ChunkingStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Preserves the original Spring AI token splitting behavior.
 */
@Component
public class TokenDocumentSplittingStrategy
    implements DocumentSplittingStrategy {

    private final TokenTextSplitter splitter;

    public TokenDocumentSplittingStrategy(RagProperties properties) {
        this.splitter = TokenTextSplitter.builder()
            .withChunkSize(properties.getChunkSize())
            .withMinChunkSizeChars(properties.getMinChunkSizeChars())
            .withMinChunkLengthToEmbed(properties.getMinChunkLengthToEmbed())
            .withMaxNumChunks(properties.getMaxNumChunks())
            .withKeepSeparator(properties.isKeepSeparator())
            .build();
    }

    @Override
    public ChunkingStrategy type() {
        return ChunkingStrategy.TOKEN;
    }

    @Override
    public List<Document> split(List<Document> documents) {
        return splitter.apply(documents);
    }
}
