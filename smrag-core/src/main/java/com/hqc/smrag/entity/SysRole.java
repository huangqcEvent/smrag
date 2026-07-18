package com.hqc.smrag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统角色实体
 * <p>
 * 对应数据库表 sys_role，存储角色的基本信息。
 * 主要字段：
 * <ul>
 *     <li>id：主键</li>
 *     <li>roleCode：角色编码（ADMIN、USER）</li>
 *     <li>roleName：角色名称</li>
 *     <li>description：角色描述</li>
 * </ul>
 *
 * @see SysUser 用户实体
 * @see SysUserRole 用户角色关联实体
 */
@Data
@TableName("sys_role")
public class SysRole {

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色编码（ADMIN、USER）
     */
    private String roleCode;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}