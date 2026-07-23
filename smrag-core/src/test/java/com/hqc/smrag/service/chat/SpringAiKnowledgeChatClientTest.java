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
import org.springframework.ai.vectorstore.filter.Filter;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    void filtersByRoleIdsWithoutExpandingAccessibleDocumentIds() throws Exception {
        SpringAiKnowledgeChatClient client = clientForFilterTests();

        Filter.Expression filter = invokeBuildFilter(
            client, "finance", 42L, List.of(5L, 8L));
        Map<String, List<Object>> equalities = equalities(filter);

        assertThat(equalities)
            .containsEntry("knowledgeBaseId", List.of("finance"))
            .containsEntry("documentStatus", List.of("COMPLETED"))
            .containsEntry("aclDenied", List.of(false))
            .containsEntry("documentId", List.of(42L))
            .containsEntry("aclRoleId_5", List.of(true))
            .containsEntry("aclRoleId_8", List.of(true));
        assertThat(countExpressions(filter, Filter.ExpressionType.OR)).isEqualTo(1);
    }

    @Test
    void deniesRetrievalWhenUserHasNoRoles() throws Exception {
        Filter.Expression filter = invokeBuildFilter(
            clientForFilterTests(), "finance", null, List.of());

        assertThat(equalities(filter))
            .containsEntry("documentId", List.of(-1L));
    }

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

    private SpringAiKnowledgeChatClient clientForFilterTests() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.clone()).thenReturn(builder);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        return new SpringAiKnowledgeChatClient(
            builder,
            mock(ChatMemory.class),
            mock(VectorStore.class),
            new RagProperties()
        );
    }

    private Filter.Expression invokeBuildFilter(
        SpringAiKnowledgeChatClient client,
        String knowledgeBaseId,
        Long documentId,
        List<Long> roleIds
    ) throws Exception {
        Method method = SpringAiKnowledgeChatClient.class.getDeclaredMethod(
            "buildFilter", String.class, Long.class, List.class);
        method.setAccessible(true);
        return (Filter.Expression) method.invoke(
            client, knowledgeBaseId, documentId, roleIds);
    }

    private Map<String, List<Object>> equalities(Filter.Operand operand) {
        Map<String, List<Object>> result = new LinkedHashMap<>();
        collectEqualities(operand, result);
        return result;
    }

    private void collectEqualities(
        Filter.Operand operand,
        Map<String, List<Object>> result
    ) {
        if (operand instanceof Filter.Group group) {
            collectEqualities(group.content(), result);
            return;
        }
        if (!(operand instanceof Filter.Expression expression)) {
            return;
        }
        if (expression.type() == Filter.ExpressionType.EQ
            && expression.left() instanceof Filter.Key key
            && expression.right() instanceof Filter.Value value) {
            result.computeIfAbsent(key.key(), ignored -> new ArrayList<>())
                .add(value.value());
            return;
        }
        collectEqualities(expression.left(), result);
        if (expression.right() != null) {
            collectEqualities(expression.right(), result);
        }
    }

    private int countExpressions(
        Filter.Operand operand,
        Filter.ExpressionType type
    ) {
        if (operand instanceof Filter.Group group) {
            return countExpressions(group.content(), type);
        }
        if (!(operand instanceof Filter.Expression expression)) {
            return 0;
        }
        int count = expression.type() == type ? 1 : 0;
        count += countExpressions(expression.left(), type);
        if (expression.right() != null) {
            count += countExpressions(expression.right(), type);
        }
        return count;
    }
}
