package com.hqc.smrag.service.chat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePromptContractTest {

    @Test
    void separatesPrivateKnowledgeFromGeneralQuestions() throws IOException {
        String prompt = new ClassPathResource(
            "prompts/structured_route_prompt.txt"
        ).getContentAsString(StandardCharsets.UTF_8);

        assertThat(prompt)
            .contains("GENERAL_CHAT")
            .contains("private", "uploaded", "general");
    }
}
