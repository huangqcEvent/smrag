package com.hqc.smrag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文档权限实体
 * <p>
 * 对应数据库表 doc_permission，存储文档的角色权限信息。
 * 主要字段：
 * <ul>
 *     <li>id：主键</li>
 *     <li>documentId：文档ID</li>
 *     <li>roleId：角色ID</li>
 *     <li>canRead：是否可读</li>
 * </ul>
 *
 * @see KnowledgeDocument 文档实体
 * @see SysRole 角色实体
 */
@Data
@TableName("doc_permission")
public class DocPermission {

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 是否可读
     */
    private Boolean canRead;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}