package com.hqc.smrag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateKnowledgeBaseRequest(
    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
    String code,
    @NotBlank @Size(max = 128) String name,
    @Size(max = 500) String description,
    @NotEmpty List<Long> roleIds
) {
}
