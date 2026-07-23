package com.hqc.smrag.controller;

import com.hqc.smrag.common.Result;
import com.hqc.smrag.dto.SetDocPermissionRequest;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.service.DocPermissionService;
import com.hqc.smrag.service.knowledge.DocumentService;
import com.hqc.smrag.vo.DocumentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Set;
import java.util.Map;
import com.hqc.smrag.vo.RoleVO;

/**
 * 知识库管理控制器
 * <p>
 * 提供知识库文档的管理接口，支持：
 * <ul>
 *     <li>上传文档：支持 PDF、Word、TXT 格式，上传后自动进行解析和向量化（仅管理员）</li>
 *     <li>查询文档列表：获取指定知识库下的所有文档（需登录）</li>
 *     <li>删除文档：删除指定文档及其相关的向量数据（仅管理员）</li>
 * </ul>
 * <p>
 * 支持多知识库隔离，通过 knowledgeBaseId 参数区分不同的知识库。
 *
 * @see DocumentService 文档服务
 * @see DocumentVO      文档视图对象
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final DocumentService documentService;
    private final DocPermissionService docPermissionService;

    /**
     * 上传知识库文档（仅管理员）
     * <p>
     * 上传文档到指定知识库，支持以下文件类型：
     * <ul>
     *     <li>PDF 文档（.pdf）</li>
     *     <li>Word 文档（.doc, .docx）</li>
     *     <li>纯文本文件（.txt）</li>
     * </ul>
     * <p>
     * 上传后，文档会被发送到 Kafka 进行异步解析和向量化处理。
     *
     * @param file            上传的文件
     * @param knowledgeBaseId 知识库ID，默认为 "default"
     * @param roles           可访问的角色列表，逗号分隔，如 "ADMIN,USER"，默认为 "ADMIN,USER"
     * @return 上传成功的文档ID
     * @throws Exception 文件上传或处理异常
     */
    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Long> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "knowledgeBaseId", defaultValue = "default") String knowledgeBaseId,
        @RequestParam(value = "roles", defaultValue = "ADMIN,USER") String roles
    ) throws Exception {
        List<String> roleCodes = java.util.Arrays.stream(roles.split(","))
            .map(String::trim)
            .filter(role -> !role.isEmpty())
            .toList();
        Long documentId =
            documentService.uploadDocument(file, knowledgeBaseId, roleCodes);

        // 设置文档权限
        return Result.success(documentId);
    }

    /**
     * 查询知识库文档列表（需登录）
     * <p>
     * 返回指定知识库下的所有文档信息，包括：
     * <ul>
     *     <li>文档基本信息（ID、名称、类型）</li>
     *     <li>处理状态（待处理、处理中、已完成、失败）</li>
     *     <li>分块数量</li>
     *     <li>创建时间</li>
     * </ul>
     * <p>
     * 普通用户只能看到有权限访问的文档。
     *
     * @param knowledgeBaseId 知识库ID，默认为 "default"
     * @param userId          当前用户ID（从 JWT 中获取）
     * @return 文档列表
     */
    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public Result<List<DocumentVO>> list(
        @RequestParam(value = "knowledgeBaseId", defaultValue = "default") String knowledgeBaseId,
        @AuthenticationPrincipal Long userId
    ) {
        // 获取用户可访问的文档ID
        List<Long> accessibleDocIds = userId != null
            ? docPermissionService.getAccessibleDocIds(userId)
            : List.of();

        List<KnowledgeDocument> allDocs = documentService.listDocuments(knowledgeBaseId);

        Set<Long> accessibleDocIdSet = Set.copyOf(accessibleDocIds);
        List<Long> documentIds = allDocs.stream().map(KnowledgeDocument::getId).toList();
        Map<Long, List<RoleVO>> rolesByDocument =
            docPermissionService.getReadableRolesByDocumentIds(documentIds);
        List<DocumentVO> documents = allDocs.stream()
            .map(doc -> {
                DocumentVO vo = new DocumentVO();
                vo.setAccessible(accessibleDocIdSet.contains(doc.getId()));
                vo.setAllowedRoles(rolesByDocument.getOrDefault(doc.getId(), List.of()));
                vo.setId(doc.getId());
                vo.setName(doc.getName());
                vo.setFileType(doc.getFileType());
                vo.setStatus(doc.getStatus());
                vo.setChunkCount(doc.getChunkCount());
                vo.setCreateTime(doc.getCreateTime());
                return vo;
            })
            .toList();
        return Result.success(documents);
    }

    /**
     * 删除知识库文档（仅管理员）
     * <p>
     * 删除指定文档，同时会：
     * <ul>
     *     <li>删除向量数据库中的相关向量</li>
     *     <li>删除数据库中的分块记录</li>
     *     <li>删除物理文件</li>
     * </ul>
     * <p>
     * 注意：删除操作不可逆，请谨慎操作。
     *
     * @param id              文档ID
     * @param knowledgeBaseId 知识库ID，默认为 "default"
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(
        @PathVariable Long id,
        @RequestParam(value = "knowledgeBaseId", defaultValue = "default") String knowledgeBaseId
    ) {
        documentService.deleteDocument(id, knowledgeBaseId);
        return Result.success();
    }
}
