package com.hqc.smrag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 安全配置属性
 *
 * @see com.hqc.smrag.security.service.JwtService JWT服务
 */
@Data
@Component
@ConfigurationProperties(prefix = "smrag.security.jwt")
public class JwtProperties {

    /**
     * JWT 密钥
     * <p>
     * 建议在生产环境使用至少 32 字节（256 位）的密钥
     */
    private String secretKey;

    /**
     * Access Token 过期时间（毫秒）
     * <p>
     * 默认：2 小时
     */
    private Long accessTokenExpiration = 7200000L;

    /**
     * Refresh Token 过期时间（毫秒）
     * <p>
     * 默认：7 天
     */
    private Long refreshTokenExpiration = 604800000L;
}