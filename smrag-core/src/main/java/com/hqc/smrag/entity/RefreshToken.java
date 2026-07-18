package com.hqc.smrag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 刷新令牌实体
 * <p>
 * 对应数据库表 refresh_token，存储刷新令牌信息。
 * 主要字段：
 * <ul>
 *     <li>id：主键</li>
 *     <li>userId：用户ID</li>
 *     <li>tokenHash：令牌哈希值</li>
 *     <li>expiresAt：过期时间</li>
 * </ul>
 *
 * @see SysUser 用户实体
 */
@Data
@TableName("refresh_token")
public class RefreshToken {

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
     * 令牌哈希值
     */
    private String tokenHash;

    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}