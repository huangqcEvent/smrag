package com.hqc.smrag.controller;

import com.hqc.smrag.dto.ChatRequestDTO;
import com.hqc.smrag.dto.ChatResponseDTO;
import com.hqc.smrag.entity.ChatSession;
import com.hqc.smrag.enums.RouteType;
import com.hqc.smrag.mapper.ChatSessionMapper;
import com.hqc.smrag.service.chat.*;
import com.hqc.smrag.service.tool.*;
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
    private final SalesScriptService salesScriptService;
    private final ChatSessionMapper sessionMapper;

    // ToolCalling工具
    private final ProductQueryTool productQueryTool;
    private final ManagerQueryTool managerQueryTool;
    private final ProductCompareTool productCompareTool;
    private final ProductRecommendTool productRecommendTool;

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

            case PRODUCT_QUERY:
                answer = productQueryTool.apply(rewritten);
                references = List.of();
                break;

            case PRODUCT_COMPARE:
                answer = productCompareTool.apply(rewritten);
                references = List.of();
                break;

            case PRODUCT_RECOMMEND:
                answer = productRecommendTool.apply(rewritten);
                references = List.of();
                break;

            case SALES_SCRIPT:
                answer = salesScriptService.generate(rewritten, "潜在客户");
                references = List.of();
                break;

            default:
                answer = "无法识别的问题类型，请重新描述您的问题";
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
        session.setReferenceDocs(references.toString());
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