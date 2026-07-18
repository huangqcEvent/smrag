package com.hqc.smrag.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hqc.smrag.entity.RefreshToken;
import com.hqc.smrag.entity.SysUser;
import com.hqc.smrag.mapper.RefreshTokenMapper;
import com.hqc.smrag.mapper.SysUserMapper;
import com.hqc.smrag.mapper.SysUserRoleMapper;
import com.hqc.smrag.security.dto.LoginRequest;
import com.hqc.smrag.security.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 认证服务
 * <p>
 * 提供用户登录、刷新令牌、登出等认证功能。
 *
 * @see JwtService JWT 服务
 * @see SysUserMapper 用户 Mapper
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final RefreshTokenMapper refreshTokenMapper;

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录响应（包含 Token 和用户信息）
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 认证用户
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // 获取用户信息
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
        );

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 获取用户角色
        List<String> roles = sysUserRoleMapper.selectRoleCodesByUserId(user.getId());

        // 生成 Access Token
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);

        // 生成 Refresh Token
        String refreshTokenValue = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusNanos(jwtService.getRefreshTokenExpiration() * 1_000_000);

        // 计算 Refresh Token 哈希值
        String refreshTokenHash = hashToken(refreshTokenValue);

        // 保存 Refresh Token
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(refreshTokenHash);
        refreshToken.setExpiresAt(expiresAt);
        refreshTokenMapper.insert(refreshToken);

        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(user);

        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());

        // 构建响应
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .expiresIn(jwtService.getAccessTokenExpiration() / 1000)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .nickname(user.getNickname())
                        .roles(roles)
                        .build())
                .build();
    }

    /**
     * 刷新令牌
     *
     * @param refreshTokenValue Refresh Token
     * @return 新的 Access Token 和 Refresh Token
     */
    @Transactional
    public LoginResponse refreshToken(String refreshTokenValue) {
        // 计算 Refresh Token 哈希值
        String refreshTokenHash = hashToken(refreshTokenValue);

        // 查找 Refresh Token
        RefreshToken refreshToken = refreshTokenMapper.selectOne(
                new LambdaQueryWrapper<RefreshToken>()
                        .eq(RefreshToken::getTokenHash, refreshTokenHash)
        );

        if (refreshToken == null) {
            throw new RuntimeException("无效的刷新令牌");
        }

        // 检查是否过期
        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenMapper.deleteById(refreshToken.getId());
            throw new RuntimeException("刷新令牌已过期");
        }

        // 获取用户信息
        SysUser user = sysUserMapper.selectById(refreshToken.getUserId());
        if (user == null || "DISABLED".equals(user.getStatus())) {
            refreshTokenMapper.deleteById(refreshToken.getId());
            throw new RuntimeException("用户不存在或已禁用");
        }

        // 获取用户角色
        List<String> roles = sysUserRoleMapper.selectRoleCodesByUserId(user.getId());

        // 生成新的 Access Token
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);

        // 生成新的 Refresh Token
        String newRefreshTokenValue = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusNanos(jwtService.getRefreshTokenExpiration() * 1_000_000);

        // 删除旧的 Refresh Token
        refreshTokenMapper.deleteById(refreshToken.getId());

        // 保存新的 Refresh Token
        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUserId(user.getId());
        newRefreshToken.setTokenHash(hashToken(newRefreshTokenValue));
        newRefreshToken.setExpiresAt(expiresAt);
        refreshTokenMapper.insert(newRefreshToken);

        log.info("刷新令牌成功: userId={}, username={}", user.getId(), user.getUsername());

        // 构建响应
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshTokenValue)
                .expiresIn(jwtService.getAccessTokenExpiration() / 1000)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .nickname(user.getNickname())
                        .roles(roles)
                        .build())
                .build();
    }

    /**
     * 用户登出
     *
     * @param userId 用户ID
     */
    @Transactional
    public void logout(Long userId) {
        // 删除用户的所有 Refresh Token
        refreshTokenMapper.deleteByUserId(userId);
        log.info("用户登出成功: userId={}", userId);
    }

    /**
     * 计算 Token 哈希值
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("计算 Token 哈希值失败", e);
        }
    }
}