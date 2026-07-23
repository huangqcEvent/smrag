package com.hqc.smrag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MoveDocumentRequest(
    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
    String targetKnowledgeBaseCode
) {
}
