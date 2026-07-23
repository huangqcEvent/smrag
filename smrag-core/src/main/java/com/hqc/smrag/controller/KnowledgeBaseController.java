package com.hqc.smrag.controller;

import com.hqc.smrag.common.Result;
import com.hqc.smrag.service.knowledge.KnowledgeBaseService;
import com.hqc.smrag.vo.KnowledgeBaseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping
    public Result<List<KnowledgeBaseVO>> list(
        @AuthenticationPrincipal Long userId
    ) {
        return Result.success(knowledgeBaseService.listAccessible(userId));
    }
}
