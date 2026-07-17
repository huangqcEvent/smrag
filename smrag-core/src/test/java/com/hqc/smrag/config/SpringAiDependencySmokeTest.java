package com.hqc.smrag.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiDependencySmokeTest {

    @Test
    void exposesRequiredSpringAiReleaseApis() {
        assertThat(MessageChatMemoryAdvisor.class).isNotNull();
        assertThat(RetrievalAugmentationAdvisor.class).isNotNull();
        assertThat(TikaDocumentReader.class).isNotNull();
        assertThat(Tool.class).isNotNull();
    }

    @Test
    void loadsSpringAiChatClientAutoConfiguration() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChatClientAutoConfiguration.class))
            .run(context -> assertThat(context).hasNotFailed());
    }
}
