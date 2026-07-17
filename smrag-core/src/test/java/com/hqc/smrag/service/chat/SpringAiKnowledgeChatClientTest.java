package com.hqc.smrag.service.chat;

import com.hqc.smrag.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiKnowledgeChatClientTest {

    @Test
    void streamsTextAndDocumentsFromTheSameAdvisorResponse() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient.ChatClientRequestSpec requestSpec =
            mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(builder.clone()).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.messages(any(List.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Advisor[].class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);

        Document evidence = Document.builder()
            .id("vector-1")
            .text("Evidence")
            .metadata(Map.of("documentName", "policy.pdf"))
            .build();
        ChatClientResponse response = mock(ChatClientResponse.class, RETURNS_DEEP_STUBS);
        when(response.chatResponse().getResult().getOutput().getText()).thenReturn("delta");
        when(response.context()).thenReturn(Map.of(
            RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT,
            List.of(evidence)
        ));
        when(streamSpec.chatClientResponse()).thenReturn(Flux.just(response));

        SpringAiKnowledgeChatClient client = new SpringAiKnowledgeChatClient(
            builder,
            mock(ChatMemory.class),
            mock(VectorStore.class),
            new RagProperties()
        );

        StepVerifier.create(client.stream("Question", "session-1", "finance", null))
            .assertNext(answer -> {
                assertThat(answer.content()).isEqualTo("delta");
                assertThat(answer.documents()).containsExactly(evidence);
            })
            .verifyComplete();

        verify(requestSpec).stream();
        verify(requestSpec).advisors(any(Advisor[].class));
    }
}
