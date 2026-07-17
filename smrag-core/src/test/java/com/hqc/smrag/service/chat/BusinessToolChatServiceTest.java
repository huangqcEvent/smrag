package com.hqc.smrag.service.chat;

import com.hqc.smrag.enums.RouteType;
import com.hqc.smrag.mapper.FundManagerMapper;
import com.hqc.smrag.mapper.FundProductMapper;
import com.hqc.smrag.service.tool.FundTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;

class BusinessToolChatServiceTest {

    private final FundTools fundTools = new FundTools(
        mock(FundProductMapper.class),
        mock(FundManagerMapper.class)
    );
    private final BusinessToolChatService service = new BusinessToolChatService(
        mock(ChatClient.class),
        mock(ChatMemory.class),
        fundTools
    );

    @Test
    void exposesExactlyOneWhitelistedToolForEachBusinessRoute() {
        Map<RouteType, String> expected = Map.of(
            RouteType.PRODUCT_QUERY, "queryProduct",
            RouteType.PRODUCT_COMPARE, "compareProducts",
            RouteType.PRODUCT_RECOMMEND, "recommendProducts",
            RouteType.MANAGER_QUERY, "queryManager"
        );

        expected.forEach((route, toolName) -> assertThat(
            service.selectedTool(route).getToolDefinition().name()
        ).isEqualTo(toolName));
    }

    @Test
    void rejectsRoutesThatMustNotReceiveBusinessTools() {
        assertThatThrownBy(() -> service.selectedTool(RouteType.KNOWLEDGE_QA))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.selectedTool(RouteType.SALES_SCRIPT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void streamsWithOnlyTheToolAllowedForTheSelectedRoute() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec =
            mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.messages(any(java.util.List.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(ToolCallback[].class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("Business ", "answer"));
        BusinessToolChatService streamingService = new BusinessToolChatService(
            chatClient,
            mock(ChatMemory.class),
            fundTools
        );

        StepVerifier.create(streamingService.stream(
                RouteType.PRODUCT_QUERY,
                "Question",
                "session-1"
            ))
            .expectNext("Business ", "answer")
            .verifyComplete();

        ArgumentCaptor<ToolCallback[]> callbacks =
            ArgumentCaptor.forClass(ToolCallback[].class);
        verify(requestSpec).toolCallbacks(callbacks.capture());
        assertThat(callbacks.getValue())
            .singleElement()
            .extracting(callback -> callback.getToolDefinition().name())
            .isEqualTo("queryProduct");
    }
}
