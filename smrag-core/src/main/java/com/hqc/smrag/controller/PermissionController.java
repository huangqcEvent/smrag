package com.hqc.smrag.controller;

import com.hqc.smrag.common.Result;
import com.hqc.smrag.dto.SetDocPermissionRequest;
import com.hqc.smrag.service.DocPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 文档权限控制器
 * <p>
 * 提供文档权限管理接口，仅管理员可访问。
 *
 * @see DocPermissionService 文档权限服务
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PermissionController {

    private final DocPermissionService docPermissionService;

    /**
     * 设置文档权限
     *
     * @param id      文档ID
     * @param request 权限请求
     * @return 空结果
     */
    @PostMapping("/{id}/permissions")
    public Result<Void> setDocumentPermissions(
            @PathVariable Long id,
            @Valid @RequestBody SetDocPermissionRequest request
    ) {
        docPermissionService.setDocumentPermissions(id, request);
        return Result.success();
    }
}