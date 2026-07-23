package com.hqc.smrag.controller;

import com.hqc.smrag.common.Result;
import com.hqc.smrag.security.dto.LoginRequest;
import com.hqc.smrag.security.dto.LoginResponse;
import com.hqc.smrag.security.dto.RefreshTokenRequest;
import com.hqc.smrag.security.service.AuthService;
import com.hqc.smrag.vo.UserVO;
import com.hqc.smrag.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * <p>
 * 提供用户登录、刷新令牌、获取当前用户信息等接口。
 *
 * @see AuthService 认证服务
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录响应（包含 Token 和用户信息）
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 刷新令牌
     *
     * @param request 刷新令牌请求
     * @return 新的登录响应
     */
    @PostMapping("/refresh")
    public Result<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refreshToken(request.getRefreshToken()));
    }

    /**
     * 获取当前用户信息
     *
     * @param userId 当前用户ID（从 JWT 中获取）
     * @return 用户信息
     */
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser(@AuthenticationPrincipal Long userId) {
        return Result.success(userService.getUserById(userId));
    }

    /**
     * 用户登出
     *
     * @param userId 当前用户ID（从 JWT 中获取）
     * @return 空结果
     */
    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return Result.success();
    }
}
