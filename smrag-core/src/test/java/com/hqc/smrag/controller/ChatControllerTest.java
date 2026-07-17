package com.hqc.smrag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hqc.smrag.dto.ChatRequestDTO;
import com.hqc.smrag.dto.ChatResponseDTO;
import com.hqc.smrag.exception.AiServiceException;
import com.hqc.smrag.service.chat.AiChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiChatService aiChatService;

    @Test
    void returnsStructuredChatResponse() throws Exception {
        ChatResponseDTO response = new ChatResponseDTO();
        response.setSessionId("session-1");
        response.setAnswer("Grounded answer");
        response.setRouteType("KNOWLEDGE_QA");
        response.setReferences(List.of("policy.pdf"));
        when(aiChatService.ask(any())).thenReturn(response);

        ChatRequestDTO request = new ChatRequestDTO();
        request.setSessionId("session-1");
        request.setQuestion("Question");

        mockMvc.perform(post("/api/chat/ask")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.answer").value("Grounded answer"))
            .andExpect(jsonPath("$.data.citations").isArray());
    }

    @Test
    void rejectsBlankQuestionWithHttp400() throws Exception {
        mockMvc.perform(post("/api/chat/ask")
                .contentType("application/json")
                .content("{\"sessionId\":\"session-1\",\"question\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(aiChatService);
    }

    @Test
    void hidesProviderDetailsFromAiErrors() throws Exception {
        when(aiChatService.ask(any())).thenThrow(new AiServiceException(
            "api-key=secret SQL failed",
            new RuntimeException("provider stack")
        ));

        mockMvc.perform(post("/api/chat/ask")
                .contentType("application/json")
                .content("{\"sessionId\":\"session-1\",\"question\":\"Question\"}"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value(502))
            .andExpect(jsonPath("$.message").value(
                "AI \u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"
            ));
    }
}
