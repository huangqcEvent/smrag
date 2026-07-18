package com.hqc.smrag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改用户角色请求 DTO
 */
@Data
public class UpdateUserRoleRequest {

    /**
     * 角色编码
     */
    @NotBlank(message = "角色不能为空")
    private String roleCode;
}