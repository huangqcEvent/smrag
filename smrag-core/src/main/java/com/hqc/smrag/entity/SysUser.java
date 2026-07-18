package com.hqc.smrag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统用户实体
 * <p>
 * 对应数据库表 sys_user，存储用户的基本信息和状态。
 * 主要字段：
 * <ul>
 *     <li>id：主键</li>
 *     <li>username：用户名（唯一）</li>
 *     <li>passwordHash：密码哈希值（BCrypt）</li>
 *     <li>nickname：用户昵称</li>
 *     <li>status：用户状态（ACTIVE、DISABLED）</li>
 *     <li>lastLoginTime：最后登录时间</li>
 * </ul>
 *
 * @see SysRole 用户角色实体
 * @see SysUserRole 用户角色关联实体
 */
@Data
@TableName("sys_user")
public class SysUser {

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名（唯一）
     */
    private String username;

    /**
     * 密码哈希值（BCrypt加密）
     */
    private String passwordHash;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 用户状态
     * <p>
     * 可能的值：ACTIVE（正常）、DISABLED（禁用）
     */
    private String status;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}