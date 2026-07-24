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
        ).getContentAsString(StandardCharsets.UTF_8)
            .replace("\r\n", "\n");

        assertThat(prompt)
            .contains("""
                - KNOWLEDGE_QA: requests that require private policies, uploaded documents,
                  selected knowledge bases, or other internal evidence.
                """)
            .contains("""
                - GENERAL_CHAT: general explanations, writing, brainstorming, or conversation
                  that can be answered without private knowledge or business tools.
                """);
    }
}
