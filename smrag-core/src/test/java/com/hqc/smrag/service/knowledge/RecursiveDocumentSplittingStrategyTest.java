package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.enums.ChunkingStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecursiveDocumentSplittingStrategyTest {

    private final Cl100kTokenCodec codec = new Cl100kTokenCodec();

    @Test
    void preservesSeparatorsMetadataAndTextWithoutOverlap() {
        RagProperties properties = properties(8, 0);
        String text = "第一段。\n\n第二段内容很长！第三段继续补充？第四段结束。";
        Document source = new Document(text, Map.of("page", 7));

        RecursiveDocumentSplittingStrategy strategy = strategy(properties);
        List<Document> chunks = strategy.split(List.of(source));

        assertThat(strategy.type()).isEqualTo(ChunkingStrategy.RECURSIVE);
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(codec.count(chunk.getText())).isLessThanOrEqualTo(8);
            assertThat(chunk.getMetadata()).containsEntry("page", 7);
        });
        assertThat(chunks.stream()
            .map(Document::getText)
            .collect(Collectors.joining())).isEqualTo(text);
    }

    @Test
    void greedilyMergesShortFragmentsAndAddsTokenOverlap() {
        RagProperties properties = properties(8, 2);
        properties.setRecursiveSeparators(List.of(", "));
        String text =
            "alpha beta, gamma delta, epsilon zeta, eta theta, iota kappa, ";

        List<Document> chunks = strategy(properties)
            .split(List.of(new Document(text)));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).getText())
            .contains("alpha beta, gamma delta, ");
        assertThat(chunks).allSatisfy(chunk ->
            assertThat(codec.count(chunk.getText())).isLessThanOrEqualTo(8));
        for (int index = 1; index < chunks.size(); index++) {
            String expectedOverlap =
                codec.lastTokens(chunks.get(index - 1).getText(), 2);
            assertThat(chunks.get(index).getText()).startsWith(expectedOverlap);
        }
    }

    @Test
    void neverMergesOrOverlapsDifferentSourceDocuments() {
        RagProperties properties = properties(100, 15);
        Document first = new Document("第一页内容。", Map.of("page", 1));
        Document second = new Document("第二页内容。", Map.of("page", 2));

        List<Document> chunks = strategy(properties).split(List.of(first, second));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getText()).isEqualTo("第一页内容。");
        assertThat(chunks.get(0).getMetadata()).containsEntry("page", 1);
        assertThat(chunks.get(1).getText()).isEqualTo("第二页内容。");
        assertThat(chunks.get(1).getMetadata()).containsEntry("page", 2);
    }

    @Test
    void fallsBackToTokenSplittingWhenNoSeparatorMatches() {
        RagProperties properties = properties(4, 0);
        properties.setRecursiveSeparators(List.of("||"));
        String text = "这是一个完全没有配置分隔符的长中文字符串";

        List<Document> chunks = strategy(properties)
            .split(List.of(new Document(text)));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk ->
            assertThat(codec.count(chunk.getText())).isLessThanOrEqualTo(4));
        assertThat(chunks.stream()
            .map(Document::getText)
            .collect(Collectors.joining())).isEqualTo(text);
    }

    @Test
    void failsInsteadOfTruncatingWhenChunkLimitIsExceeded() {
        RagProperties properties = properties(4, 0);
        properties.setRecursiveSeparators(List.of("||"));
        properties.setMaxNumChunks(1);

        assertThatThrownBy(() -> strategy(properties).split(
            List.of(new Document("这是一个会产生多个分块的长中文字符串"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("上限");
    }

    @Test
    void failsWhenNonBlankSourceCannotProduceEmbeddableChunk() {
        RagProperties properties = properties(100, 0);
        properties.setMinChunkLengthToEmbed(10);

        assertThatThrownBy(() -> strategy(properties)
            .split(List.of(new Document("short"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("有效");
    }

    private RecursiveDocumentSplittingStrategy strategy(RagProperties properties) {
        return new RecursiveDocumentSplittingStrategy(properties, codec);
    }

    private RagProperties properties(int chunkSize, int overlap) {
        RagProperties properties = new RagProperties();
        properties.setRecursiveChunkSize(chunkSize);
        properties.setRecursiveChunkOverlap(overlap);
        properties.setMinChunkLengthToEmbed(1);
        properties.setMaxNumChunks(100);
        return properties;
    }
}
