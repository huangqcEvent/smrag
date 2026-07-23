package com.hqc.smrag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record SetKnowledgeBaseStatusRequest(
    @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
    @NotNull @Positive Integer version
) {
}
