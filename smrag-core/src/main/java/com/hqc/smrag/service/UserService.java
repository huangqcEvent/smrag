package com.hqc.smrag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hqc.smrag.dto.AddUserRequest;
import com.hqc.smrag.dto.UpdateUserRoleRequest;
import com.hqc.smrag.entity.SysRole;
import com.hqc.smrag.entity.SysUser;
import com.hqc.smrag.entity.SysUserRole;
import com.hqc.smrag.mapper.SysRoleMapper;
import com.hqc.smrag.mapper.SysUserMapper;
import com.hqc.smrag.mapper.SysUserRoleMapper;
import com.hqc.smrag.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户管理服务
 *
 * @see SysUserMapper 用户 Mapper
 * @see SysRoleMapper 角色 Mapper
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 获取所有用户列表
     *
     * @return 用户列表
     */
    public List<UserVO> getAllUsers() {
        List<SysUser> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .orderByDesc(SysUser::getCreatedAt)
        );

        return users.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 根据ID获取用户
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    public UserVO getUserById(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return convertToVO(user);
    }

    /**
     * 添加用户
     *
     * @param request 添加用户请求
     * @return 用户ID
     */
    @Transactional
    public Long addUser(AddUserRequest request) {
        // 检查用户名是否已存在
        Long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
        );
        if (count > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 查找角色
        SysRole role = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode, request.getRoleCode())
        );
        if (role == null) {
            throw new RuntimeException("角色不存在");
        }

        // 创建用户
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(user);

        // 关联角色
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRole.setCreatedAt(LocalDateTime.now());
        sysUserRoleMapper.insert(userRole);

        log.info("添加用户成功: userId={}, username={}, role={}", user.getId(), user.getUsername(), request.getRoleCode());

        return user.getId();
    }

    /**
     * 修改用户角色
     *
     * @param userId  用户ID
     * @param request 修改角色请求
     */
    @Transactional
    public void updateUserRole(Long userId, UpdateUserRoleRequest request) {
        // 检查用户是否存在
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 查找角色
        SysRole role = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode, request.getRoleCode())
        );
        if (role == null) {
            throw new RuntimeException("角色不存在");
        }

        // 删除旧的角色关联
        sysUserRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
        );

        // 添加新的角色关联
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRole.setCreatedAt(LocalDateTime.now());
        sysUserRoleMapper.insert(userRole);

        log.info("修改用户角色成功: userId={}, newRole={}", userId, request.getRoleCode());
    }

    /**
     * 重置用户密码
     *
     * @param userId      用户ID
     * @param newPassword 新密码
     */
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);

        log.info("重置用户密码成功: userId={}", userId);
    }

    /**
     * 禁用/启用用户
     *
     * @param userId  用户ID
     * @param disable 是否禁用
     */
    @Transactional
    public void toggleUserStatus(Long userId, boolean disable) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        user.setStatus(disable ? "DISABLED" : "ACTIVE");
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);

        log.info("用户状态更新: userId={}, status={}", userId, user.getStatus());
    }

    /**
     * 转换为 VO
     */
    private UserVO convertToVO(SysUser user) {
        List<String> roles = sysUserRoleMapper.selectRoleCodesByUserId(user.getId());
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .status(user.getStatus())
                .roles(roles)
                .lastLoginTime(user.getLastLoginTime())
                .createdAt(user.getCreatedAt())
                .build();
    }
}