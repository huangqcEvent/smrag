package com.hqc.smrag.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record SetKnowledgeBaseRolesRequest(
    @NotEmpty List<Long> roleIds,
    @NotNull @Positive Integer version
) {
}
