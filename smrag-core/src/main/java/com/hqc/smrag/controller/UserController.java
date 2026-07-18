package com.hqc.smrag.controller;

import com.hqc.smrag.common.Result;
import com.hqc.smrag.dto.AddUserRequest;
import com.hqc.smrag.dto.UpdateUserRoleRequest;
import com.hqc.smrag.service.UserService;
import com.hqc.smrag.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 * <p>
 * 提供用户管理接口，仅管理员可访问。
 *
 * @see UserService 用户服务
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    /**
     * 获取所有用户列表
     *
     * @return 用户列表
     */
    @GetMapping
    public Result<List<UserVO>> getAllUsers() {
        return Result.success(userService.getAllUsers());
    }

    /**
     * 获取用户信息
     *
     * @param id 用户ID
     * @return 用户信息
     */
    @GetMapping("/{id}")
    public Result<UserVO> getUserById(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    /**
     * 添加用户
     *
     * @param request 添加用户请求
     * @return 用户ID
     */
    @PostMapping
    public Result<Long> addUser(@Valid @RequestBody AddUserRequest request) {
        return Result.success(userService.addUser(request));
    }

    /**
     * 修改用户角色
     *
     * @param id      用户ID
     * @param request 修改角色请求
     * @return 空结果
     */
    @PutMapping("/{id}/roles")
    public Result<Void> updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        userService.updateUserRole(id, request);
        return Result.success();
    }

    /**
     * 重置用户密码
     *
     * @param id          用户ID
     * @param newPassword 新密码
     * @return 空结果
     */
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(
            @PathVariable Long id,
            @RequestParam String newPassword
    ) {
        userService.resetPassword(id, newPassword);
        return Result.success();
    }

    /**
     * 禁用用户
     *
     * @param id 用户ID
     * @return 空结果
     */
    @PutMapping("/{id}/disable")
    public Result<Void> disableUser(@PathVariable Long id) {
        userService.toggleUserStatus(id, true);
        return Result.success();
    }

    /**
     * 启用用户
     *
     * @param id 用户ID
     * @return 空结果
     */
    @PutMapping("/{id}/enable")
    public Result<Void> enableUser(@PathVariable Long id) {
        userService.toggleUserStatus(id, false);
        return Result.success();
    }
}