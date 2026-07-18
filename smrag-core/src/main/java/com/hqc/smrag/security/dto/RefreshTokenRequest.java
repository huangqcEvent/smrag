package com.hqc.smrag.security.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新令牌请求 DTO
 */
@Data
public class RefreshTokenRequest {

    /**
     * Refresh Token
     */
    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}