package com.hqc.smrag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_base_role")
public class KnowledgeBaseRole {

    private Long knowledgeBaseId;

    private Long roleId;

    private Long createdBy;

    private LocalDateTime createdAt;
}
