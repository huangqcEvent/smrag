package com.hqc.smrag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@TableName("knowledge_document")
public class KnowledgeDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String filePath;
    private String fileType;
    private String status;
    private Integer chunkCount;
    private String knowledgeBaseId;
    private String checksum;
    private Integer version;
    private String failureReason;
    private UUID processingToken;
    private LocalDateTime processingStartedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
