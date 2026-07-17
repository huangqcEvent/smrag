package com.hqc.smrag.controller;

import com.hqc.smrag.common.Result;
import com.hqc.smrag.service.knowledge.DocumentService;
import com.hqc.smrag.vo.DocumentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public Result<Long> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "knowledgeBaseId", defaultValue = "default") String knowledgeBaseId
    ) throws Exception {
        return Result.success(documentService.uploadDocument(file, knowledgeBaseId));
    }

    @GetMapping("/list")
    public Result<List<DocumentVO>> list(
        @RequestParam(value = "knowledgeBaseId", defaultValue = "default") String knowledgeBaseId
    ) {
        List<DocumentVO> documents = documentService.listDocuments(knowledgeBaseId).stream()
            .map(doc -> {
                DocumentVO vo = new DocumentVO();
                vo.setId(doc.getId());
                vo.setName(doc.getName());
                vo.setFileType(doc.getFileType());
                vo.setStatus(doc.getStatus());
                vo.setChunkCount(doc.getChunkCount());
                vo.setCreateTime(doc.getCreateTime());
                return vo;
            })
            .collect(Collectors.toList());
        return Result.success(documents);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
        @PathVariable Long id,
        @RequestParam(value = "knowledgeBaseId", defaultValue = "default") String knowledgeBaseId
    ) {
        documentService.deleteDocument(id, knowledgeBaseId);
        return Result.success();
    }
}
