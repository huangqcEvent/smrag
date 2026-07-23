package com.hqc.smrag.controller;

import com.hqc.smrag.common.Result;
import com.hqc.smrag.dto.CreateKnowledgeBaseRequest;
import com.hqc.smrag.dto.SetKnowledgeBaseRolesRequest;
import com.hqc.smrag.dto.SetKnowledgeBaseStatusRequest;
import com.hqc.smrag.dto.UpdateKnowledgeBaseRequest;
import com.hqc.smrag.service.knowledge.KnowledgeBaseService;
import com.hqc.smrag.vo.KnowledgeBaseAdminVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/knowledge-bases")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminKnowledgeBaseController {

    private final KnowledgeBaseService service;

    @GetMapping
    public Result<List<KnowledgeBaseAdminVO>> list() {
        return Result.success(service.listAdmin());
    }

    @PostMapping
    public Result<Long> create(
        @Valid @RequestBody CreateKnowledgeBaseRequest request,
        @AuthenticationPrincipal Long userId
    ) {
        return Result.success(service.create(request, userId));
    }

    @PutMapping("/{id}")
    public Result<Void> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateKnowledgeBaseRequest request,
        @AuthenticationPrincipal Long userId
    ) {
        service.update(id, request, userId);
        return Result.success();
    }

    @PutMapping("/{id}/roles")
    public Result<Void> replaceRoles(
        @PathVariable Long id,
        @Valid @RequestBody SetKnowledgeBaseRolesRequest request,
        @AuthenticationPrincipal Long userId
    ) {
        service.replaceRoles(id, request, userId);
        return Result.success();
    }

    @PatchMapping("/{id}/status")
    public Result<Void> changeStatus(
        @PathVariable Long id,
        @Valid @RequestBody SetKnowledgeBaseStatusRequest request,
        @AuthenticationPrincipal Long userId
    ) {
        service.changeStatus(id, request, userId);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal Long userId
    ) {
        service.delete(id, userId);
        return Result.success();
    }
}
