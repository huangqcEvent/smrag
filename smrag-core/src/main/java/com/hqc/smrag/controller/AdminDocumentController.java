package com.hqc.smrag.controller;

import com.hqc.smrag.common.Result;
import com.hqc.smrag.dto.MoveDocumentRequest;
import com.hqc.smrag.service.knowledge.DocumentTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/documents")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDocumentController {

    private final DocumentTransferService transferService;

    @PostMapping("/{id}/move")
    public Result<Void> move(
        @PathVariable Long id,
        @Valid @RequestBody MoveDocumentRequest request
    ) {
        transferService.move(id, request);
        return Result.success();
    }
}
