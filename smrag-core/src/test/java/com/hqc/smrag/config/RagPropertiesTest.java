package com.hqc.smrag.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagPropertiesTest {

    private final Validator validator = Validation
        .buildDefaultValidatorFactory()
        .getValidator();

    @Test
    void usesRecallFriendlySimilarityThresholdByDefaultAndInDeploymentConfig() throws Exception {
        RagProperties properties = new RagProperties();
        Object configuredThreshold = new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"))
            .stream()
            .map(source -> source.getProperty("smrag.ai.rag.similarity-threshold"))
            .filter(value -> value != null)
            .findFirst()
            .orElse(null);

        assertThat(properties.getSimilarityThreshold()).isEqualTo(0.40);
        assertThat(configuredThreshold).isEqualTo(0.40);
    }

    @Test
    void recursiveDefaultsAreValidAndMatchDeploymentConfig() throws Exception {
        RagProperties properties = new RagProperties();
        var configured = new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"))
            .get(0);

        assertThat(properties.getRecursiveChunkSize()).isEqualTo(800);
        assertThat(properties.getRecursiveChunkOverlap()).isEqualTo(120);
        assertThat(properties.getRecursiveSeparators())
            .containsExactly("\n\n", "\n", "。", "！", "？", "；", "，", " ");
        assertThat(validator.validate(properties)).isEmpty();
        assertThat(configured.getProperty("smrag.ai.rag.recursive-chunk-size"))
            .isEqualTo(800);
        assertThat(configured.getProperty("smrag.ai.rag.recursive-chunk-overlap"))
            .isEqualTo(120);
        assertThat(configured.getProperty("smrag.ai.rag.recursive-separators[0]"))
            .isEqualTo("\n\n");
    }

    @Test
    void recursiveOverlapMustBeSmallerThanChunkSize() {
        RagProperties properties = new RagProperties();
        properties.setRecursiveChunkSize(120);
        properties.setRecursiveChunkOverlap(120);

        assertThat(validator.validate(properties)).isNotEmpty();
    }

    @Test
    void recursiveSeparatorsMustBeNonEmptyAndUnique() {
        RagProperties properties = new RagProperties();
        properties.setRecursiveSeparators(List.of("\n", "\n"));

        assertThat(validator.validate(properties)).isNotEmpty();

        properties.setRecursiveSeparators(List.of("\n", ""));
        assertThat(validator.validate(properties)).isNotEmpty();
    }
}
