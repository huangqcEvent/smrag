package com.hqc.smrag.config;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EmbeddingBatchingConfiguration {

    @Bean
    BatchingStrategy pgVectorEmbeddingBatchingStrategy() {
        return new DashScopeEmbeddingBatchingStrategy();
    }
}
