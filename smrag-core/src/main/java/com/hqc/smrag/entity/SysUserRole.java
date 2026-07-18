package com.hqc.smrag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户角色关联实体
 * <p>
 * 对应数据库表 sys_user_role，存储用户和角色的关联关系。
 * 主要字段：
 * <ul>
 *     <li>id：主键</li>
 *     <li>userId：用户ID</li>
 *     <li>roleId：角色ID</li>
 * </ul>
 *
 * @see SysUser 用户实体
 * @see SysRole 角色实体
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}