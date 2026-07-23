package com.hqc.smrag.vo;

import java.time.LocalDateTime;
import java.util.List;

public record KnowledgeBaseAdminVO(
    Long id,
    String code,
    String name,
    String description,
    String status,
    Integer version,
    long documentCount,
    List<RoleVO> roles,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
