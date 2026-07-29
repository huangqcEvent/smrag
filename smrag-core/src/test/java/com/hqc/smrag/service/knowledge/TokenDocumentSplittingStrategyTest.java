package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.enums.ChunkingStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TokenDocumentSplittingStrategyTest {

    @Test
    void delegatesToExistingTokenTextSplitterConfiguration() {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(10);
        properties.setMinChunkSizeChars(1);
        properties.setMinChunkLengthToEmbed(1);
        properties.setMaxNumChunks(20);
        properties.setKeepSeparator(true);
        List<Document> sources = List.of(new Document(
            "First sentence. Second sentence. Third sentence. Fourth sentence.",
            Map.of("page", 3)
        ));
        TokenTextSplitter existing = TokenTextSplitter.builder()
            .withChunkSize(properties.getChunkSize())
            .withMinChunkSizeChars(properties.getMinChunkSizeChars())
            .withMinChunkLengthToEmbed(properties.getMinChunkLengthToEmbed())
            .withMaxNumChunks(properties.getMaxNumChunks())
            .withKeepSeparator(properties.isKeepSeparator())
            .build();

        TokenDocumentSplittingStrategy strategy =
            new TokenDocumentSplittingStrategy(properties);
        List<Document> expected = existing.apply(sources);
        List<Document> actual = strategy.split(sources);

        assertThat(strategy.type()).isEqualTo(ChunkingStrategy.TOKEN);
        assertThat(actual).extracting(Document::getText)
            .containsExactlyElementsOf(
                expected.stream().map(Document::getText).toList()
            );
        assertThat(actual).extracting(Document::getMetadata)
            .containsExactlyElementsOf(
                expected.stream().map(Document::getMetadata).toList()
            );
    }
}
