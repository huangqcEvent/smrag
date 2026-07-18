package com.hqc.smrag.security.service;

import com.hqc.smrag.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 服务
 * <p>
 * 提供 JWT Token 的生成、解析和验证功能。
 *
 * @see JwtProperties JWT 配置属性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 Access Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param roles    角色列表
     * @return JWT Token
     */
    public String generateAccessToken(Long userId, String username, List<String> roles) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getAccessTokenExpiration()))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 Token 获取 Claims
     *
     * @param token JWT Token
     * @return Claims
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            throw new JwtException("Token 已过期");
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
            throw new JwtException("不支持的 Token 格式");
        } catch (MalformedJwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            throw new JwtException("无效的 Token 格式");
        } catch (SecurityException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
            throw new JwtException("无效的 Token 签名");
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is empty: {}", e.getMessage());
            throw new JwtException("Token 不能为空");
        }
    }

    /**
     * 验证 Token 是否有效
     *
     * @param token JWT Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * 从 Token 中获取用户ID
     *
     * @param token JWT Token
     * @return 用户ID
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 Token 中获取用户名
     *
     * @param token JWT Token
     * @return 用户名
     */
    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    /**
     * 从 Token 中获取角色列表
     *
     * @param token JWT Token
     * @return 角色列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return parseToken(token).get("roles", List.class);
    }

    /**
     * 获取 Access Token 过期时间（毫秒）
     */
    public Long getAccessTokenExpiration() {
        return jwtProperties.getAccessTokenExpiration();
    }

    /**
     * 获取 Refresh Token 过期时间（毫秒）
     */
    public Long getRefreshTokenExpiration() {
        return jwtProperties.getRefreshTokenExpiration();
    }
}