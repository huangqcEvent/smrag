package com.hqc.smrag.controller;

import com.hqc.smrag.dto.ChatStreamEvent;
import com.hqc.smrag.security.JwtAuthenticationEntryPoint;
import com.hqc.smrag.mapper.SysUserRoleMapper;
import com.hqc.smrag.security.config.SecurityConfig;
import com.hqc.smrag.security.service.JwtService;
import com.hqc.smrag.service.chat.AiChatService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
class ChatStreamSecurityTest {

    private static final String TOKEN = "valid-token";

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private SysUserRoleMapper userRoleMapper;

    @MockitoBean
    private AiChatService aiChatService;

    @jakarta.annotation.Resource
    private MockMvc mockMvc;

    @Test
    void authenticatedSseAsyncDispatchKeepsStreamingResponseAuthorized() throws Exception {
        when(jwtService.validateToken(TOKEN)).thenReturn(true);
        when(jwtService.getUserId(TOKEN)).thenReturn(7L);
        when(jwtService.getUsername(TOKEN)).thenReturn("admin");
        when(userRoleMapper.selectRoleCodesByUserId(7L))
            .thenReturn(List.of("ADMIN"));
        when(aiChatService.stream(any())).thenReturn(Flux.just(
            ChatStreamEvent.status("ANALYZING"),
            ChatStreamEvent.delta("ok"),
            ChatStreamEvent.done("session-1", "KNOWLEDGE_QA")
        ));

        MvcResult result = mockMvc.perform(post("/api/chat/stream")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .contentType("application/json")
                .accept("text/event-stream")
                .content("{\"sessionId\":\"session-1\",\"question\":\"Question\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
    }
}
