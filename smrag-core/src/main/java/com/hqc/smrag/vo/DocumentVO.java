package com.hqc.smrag.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DocumentVO {
    private Long id;
    private String name;
    private String fileType;
    private String status;
    private Integer chunkCount;
    private LocalDateTime createTime;
}