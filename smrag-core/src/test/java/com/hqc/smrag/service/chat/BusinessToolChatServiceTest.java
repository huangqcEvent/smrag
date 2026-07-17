package com.hqc.smrag.service.chat;

import com.hqc.smrag.enums.RouteType;
import com.hqc.smrag.mapper.FundManagerMapper;
import com.hqc.smrag.mapper.FundProductMapper;
import com.hqc.smrag.service.tool.FundTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}
