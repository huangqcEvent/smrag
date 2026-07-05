package com.hqc.smrag.controller;

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
    public Long upload(@RequestParam("file") MultipartFile file) throws Exception {
        return documentService.uploadDocument(file);
    }

    @GetMapping("/list")
    public List<DocumentVO> list() {
        return documentService.listDocuments().stream()
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
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        documentService.deleteDocument(id);
    }
}