package com.hqc.smrag.controller;

import com.hqc.smrag.common.Result;
import com.hqc.smrag.dto.ChatRequestDTO;
import com.hqc.smrag.dto.ChatResponseDTO;
import com.hqc.smrag.entity.ChatSession;
import com.hqc.smrag.service.chat.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AiChatService aiChatService;

    @PostMapping("/ask")
    public Result<ChatResponseDTO> ask(@Valid @RequestBody ChatRequestDTO request) {
        return Result.success(aiChatService.ask(request));
    }

    @GetMapping("/history/{sessionId}")
    public Result<List<ChatSession>> history(@PathVariable String sessionId) {
        return Result.success(aiChatService.history(sessionId));
    }
}
