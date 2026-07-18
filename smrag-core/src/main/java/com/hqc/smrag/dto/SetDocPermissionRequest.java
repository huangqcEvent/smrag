package com.hqc.smrag.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 设置文档权限请求 DTO
 */
@Data
public class SetDocPermissionRequest {

    /**
     * 角色权限列表
     */
    @NotNull(message = "权限列表不能为空")
    private List<RolePermission> permissions;

    /**
     * 角色权限
     */
    @Data
    public static class RolePermission {
        /**
         * 角色编码
         */
        @NotNull(message = "角色编码不能为空")
        private String roleCode;

        /**
         * 是否可读
         */
        private Boolean canRead = true;
    }
}