package com.hqc.smrag.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class RagPropertiesTest {

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
}
