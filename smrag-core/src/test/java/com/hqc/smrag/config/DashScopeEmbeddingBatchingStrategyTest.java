package com.hqc.smrag.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DashScopeEmbeddingBatchingStrategyTest {

    @Test
    void limitsEveryEmbeddingBatchToTwentyFiveDocuments() {
        List<Document> documents = documents(26);
        BatchingStrategy delegate = input -> List.of(input);

        List<List<Document>> batches =
            new DashScopeEmbeddingBatchingStrategy(delegate).batch(documents);

        assertThat(batches).extracting(List::size).containsExactly(25, 1);
        assertThat(batches.stream().flatMap(List::stream).toList())
            .containsExactlyElementsOf(documents);
    }

    @Test
    void preservesDelegateBatchBoundariesWhileApplyingDocumentLimit() {
        List<Document> documents = documents(60);
        BatchingStrategy delegate = input ->
            List.of(input.subList(0, 40), input.subList(40, 60));

        List<List<Document>> batches =
            new DashScopeEmbeddingBatchingStrategy(delegate).batch(documents);

        assertThat(batches).extracting(List::size).containsExactly(25, 15, 20);
        assertThat(batches.stream().flatMap(List::stream).toList())
            .containsExactlyElementsOf(documents);
    }

    @Test
    void configurationRegistersApplicationBatchingStrategy() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(EmbeddingBatchingConfiguration.class)) {
            assertThat(context.getBean(BatchingStrategy.class))
                .isInstanceOf(DashScopeEmbeddingBatchingStrategy.class);
        }
    }

    private List<Document> documents(int count) {
        return IntStream.range(0, count)
            .mapToObj(index -> new Document("chunk-" + index))
            .toList();
    }
}
