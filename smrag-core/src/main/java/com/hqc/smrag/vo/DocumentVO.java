package com.hqc.smrag.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档视图对象（VO）
 * <p>
 * 用于前端展示的文档信息，包括：
 * <ul>
 *     <li>id：文档ID</li>
 *     <li>name：文档名称</li>
 *     <li>fileType：文件类型（PDF、WORD、TXT）</li>
 *     <li>status：处理状态</li>
 *     <li>chunkCount：分块数量</li>
 *     <li>createTime：创建时间</li>
 * </ul>
 * <p>
 * 与 {@link com.hqc.smrag.entity.KnowledgeDocument} 实体的区别：
 * VO 只包含前端需要展示的字段，不包含敏感信息（如文件路径、校验和等）。
 *
 * @see com.hqc.smrag.controller.KnowledgeController 知识库控制器
 */
@Data
public class DocumentVO {

    private boolean accessible;

    private List<RoleVO> allowedRoles = List.of();

    /**
     * 文档ID
     */
    private Long id;

    /**
     * 文档名称
     */
    private String name;

    /**
     * 文件类型（PDF、WORD、TXT）
     */
    private String fileType;

    /**
     * 处理状态
     * <p>
     * 可能的值：PENDING、PROCESSING、COMPLETED、FAILED。
     */
    private String status;

    /**
     * 文档切块策略：TOKEN 或 RECURSIVE。
     */
    private String chunkingStrategy;

    /**
     * 分块数量
     * <p>
     * 文档被切分为多少个块进行向量化处理。
     */
    private Integer chunkCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
