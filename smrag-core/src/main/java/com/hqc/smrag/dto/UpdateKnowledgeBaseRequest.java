package com.hqc.smrag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateKnowledgeBaseRequest(
    @NotBlank @Size(max = 128) String name,
    @Size(max = 500) String description,
    @NotNull @Positive Integer version
) {
}
