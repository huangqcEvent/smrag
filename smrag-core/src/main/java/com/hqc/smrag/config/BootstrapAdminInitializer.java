package com.hqc.smrag.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hqc.smrag.entity.SysRole;
import com.hqc.smrag.entity.SysUser;
import com.hqc.smrag.entity.SysUserRole;
import com.hqc.smrag.mapper.SysRoleMapper;
import com.hqc.smrag.mapper.SysUserMapper;
import com.hqc.smrag.mapper.SysUserRoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Creates or unlocks the first global administrator without shipping a usable
 * default password.
 */
@Slf4j
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    static final String LOCKED_PASSWORD_HASH = "!BOOTSTRAP_ADMIN_PASSWORD_REQUIRED!";
    private static final String GLOBAL_ADMIN_ROLE_CODE = "ADMIN";
    private static final int MINIMUM_PASSWORD_LENGTH = 12;

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String bootstrapPassword;

    public BootstrapAdminInitializer(
        SysUserMapper userMapper,
        SysRoleMapper roleMapper,
        SysUserRoleMapper userRoleMapper,
        PasswordEncoder passwordEncoder,
        @Value("${smrag.security.bootstrap-admin.username:admin}") String username,
        @Value("${smrag.security.bootstrap-admin.password:}") String bootstrapPassword
    ) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        SysUser administrator = userMapper.selectOne(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
        );

        boolean passwordInitializationRequired = administrator == null
            || LOCKED_PASSWORD_HASH.equals(administrator.getPasswordHash());

        if (passwordInitializationRequired) {
            validateBootstrapPassword();
            administrator = initializePassword(administrator);
        }

        ensureGlobalAdminRole(administrator);
    }

    private void validateBootstrapPassword() {
        if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
            throw new IllegalStateException(
                "Set SMRAG_BOOTSTRAP_ADMIN_PASSWORD before starting FundRAG"
            );
        }
        if (bootstrapPassword.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                "SMRAG_BOOTSTRAP_ADMIN_PASSWORD must contain at least "
                    + MINIMUM_PASSWORD_LENGTH + " characters"
            );
        }
    }

    private SysUser initializePassword(SysUser administrator) {
        LocalDateTime now = LocalDateTime.now();
        if (administrator == null) {
            administrator = new SysUser();
            administrator.setUsername(username);
            administrator.setNickname("系统管理员");
            administrator.setStatus("ACTIVE");
            administrator.setCreatedAt(now);
            administrator.setUpdatedAt(now);
            administrator.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
            userMapper.insert(administrator);
            log.info("Created bootstrap administrator account: {}", username);
            return administrator;
        }

        administrator.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
        administrator.setUpdatedAt(now);
        userMapper.updateById(administrator);
        log.info("Initialized password for bootstrap administrator account: {}", username);
        return administrator;
    }

    private void ensureGlobalAdminRole(SysUser administrator) {
        SysRole globalAdminRole = roleMapper.selectOne(
            new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, GLOBAL_ADMIN_ROLE_CODE)
        );
        if (globalAdminRole == null) {
            throw new IllegalStateException("Global ADMIN role is missing");
        }

        if (userRoleMapper.userHasRole(administrator.getId(), GLOBAL_ADMIN_ROLE_CODE)) {
            return;
        }

        SysUserRole assignment = new SysUserRole();
        assignment.setUserId(administrator.getId());
        assignment.setRoleId(globalAdminRole.getId());
        assignment.setCreatedAt(LocalDateTime.now());
        userRoleMapper.insert(assignment);
    }
}
