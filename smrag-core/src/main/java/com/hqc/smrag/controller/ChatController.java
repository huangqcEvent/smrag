package com.hqc.smrag.controller;

import com.hqc.smrag.dto.ChatRequestDTO;
import com.hqc.smrag.dto.ChatResponseDTO;
import com.hqc.smrag.entity.ChatSession;
import com.hqc.smrag.enums.RouteType;
import com.hqc.smrag.mapper.ChatSessionMapper;
import com.hqc.smrag.service.chat.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final QuestionRewriteService rewriteService;
    private final RouteService routeService;
    private final RagService ragService;
    private final AnswerService answerService;
    private final ChatSessionMapper sessionMapper;

    @PostMapping("/ask")
    public ChatResponseDTO ask(@RequestBody ChatRequestDTO request) {
        String sessionId = request.getSessionId();
        String question = request.getQuestion();

        // 问题改写
        String rewritten = rewriteService.rewrite(question, sessionId);

        // 路由分类
        RouteType routeType = routeService.route(rewritten);

        // 根据路由类型处理
        String answer;
        List<String> references;

        switch (routeType) {
            case KNOWLEDGE_QA:
                List<String> contexts = ragService.retrieve(rewritten);
                answer = answerService.generate(rewritten, contexts);
                references = ragService.retrieveReferences(rewritten);
                break;

            default:
                answer = "该功能正在开发中，请稍后使用";
                references = List.of();
        }

        // 保存会话记录
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(request.getUserId());
        session.setQuestion(question);
        session.setRewriteQuestion(rewritten);
        session.setAnswer(answer);
        session.setRouteType(routeType.name());
        session.setReferences(references.toString());
        session.setCreateTime(LocalDateTime.now());
        sessionMapper.insert(session);

        // 返回结果
        ChatResponseDTO response = new ChatResponseDTO();
        response.setAnswer(answer);
        response.setRouteType(routeType.name());
        response.setReferences(references);
        response.setSessionId(sessionId);

        return response;
    }

    @GetMapping("/history/{sessionId}")
    public List<ChatSession> history(@PathVariable String sessionId) {
        return sessionMapper.findBySessionId(sessionId);
    }
}