package com.hqc.smrag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 知识库分块实体
 * <p>
 * 对应数据库表 knowledge_chunk，存储文档分块的详细信息。
 * 每个文档会被切分为多个分块，每个分块对应向量数据库中的一条记录。
 * <p>
 * 主要字段：
 * <ul>
 *     <li>id：主键</li>
 *     <li>documentId：所属文档ID</li>
 *     <li>content：分块内容</li>
 *     <li>chunkIndex：分块索引（从 0 开始）</li>
 *     <li>vectorId：向量ID，对应向量数据库中的记录</li>
 *     <li>pageNumber：页码（PDF文档）</li>
 *     <li>documentVersion：文档版本号</li>
 * </ul>
 *
 * @see KnowledgeDocument 文档实体
 */
@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属文档ID
     */
    private Long documentId;

    /**
     * 分块内容
     */
    private String content;

    /**
     * 分块索引
     * <p>
     * 从 0 开始，表示在文档中的顺序。
     */
    private Integer chunkIndex;

    /**
     * 向量ID
     * <p>
     * 对应向量数据库中的记录ID。
     */
    private UUID vectorId;

    /**
     * 页码
     * <p>
     * 对于 PDF 文档，表示内容所在的页码。
     */
    private Integer pageNumber;

    /**
     * 文档版本号
     * <p>
     * 记录分块对应的文档版本，用于版本控制。
     */
    private Integer documentVersion;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
