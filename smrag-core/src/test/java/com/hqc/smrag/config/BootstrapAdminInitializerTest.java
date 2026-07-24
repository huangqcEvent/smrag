package com.hqc.smrag.config;

import com.hqc.smrag.entity.SysRole;
import com.hqc.smrag.entity.SysUser;
import com.hqc.smrag.mapper.SysRoleMapper;
import com.hqc.smrag.mapper.SysUserMapper;
import com.hqc.smrag.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminInitializerTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    private SysRole adminRole;

    @BeforeEach
    void setUp() {
        adminRole = new SysRole();
        adminRole.setId(1L);
        adminRole.setRoleCode("ADMIN");
    }

    @Test
    void rejectsASeededAdminUntilAStrongBootstrapPasswordIsProvided() {
        SysUser admin = user(1L, BootstrapAdminInitializer.LOCKED_PASSWORD_HASH);
        when(userMapper.selectOne(any())).thenReturn(admin);

        BootstrapAdminInitializer initializer = initializer("");

        assertThatThrownBy(() -> initializer.run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SMRAG_BOOTSTRAP_ADMIN_PASSWORD");
    }

    @Test
    void replacesTheLockedPasswordAndEnsuresTheGlobalAdminRole() throws Exception {
        SysUser admin = user(1L, BootstrapAdminInitializer.LOCKED_PASSWORD_HASH);
        when(userMapper.selectOne(any())).thenReturn(admin);
        when(roleMapper.selectOne(any())).thenReturn(adminRole);
        when(passwordEncoder.encode("a-strong-bootstrap-password")).thenReturn("bcrypt-hash");

        initializer("a-strong-bootstrap-password").run(null);

        assertThat(admin.getPasswordHash()).isEqualTo("bcrypt-hash");
        verify(userMapper).updateById(admin);
        verify(userRoleMapper).insert(any(com.hqc.smrag.entity.SysUserRole.class));
    }

    @Test
    void createsTheAdminWhenTheDatabaseHasNoBootstrapUser() throws Exception {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(roleMapper.selectOne(any())).thenReturn(adminRole);
        when(passwordEncoder.encode("a-strong-bootstrap-password")).thenReturn("bcrypt-hash");
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser inserted = invocation.getArgument(0);
            inserted.setId(42L);
            return 1;
        });

        initializer("a-strong-bootstrap-password").run(null);

        verify(userMapper).insert(any(SysUser.class));
        verify(userRoleMapper).insert(any(com.hqc.smrag.entity.SysUserRole.class));
    }

    @Test
    void neverOverwritesAnAdministratorPasswordThatWasAlreadyChanged() throws Exception {
        SysUser admin = user(1L, "custom-bcrypt-hash");
        when(userMapper.selectOne(any())).thenReturn(admin);
        when(roleMapper.selectOne(any())).thenReturn(adminRole);
        when(userRoleMapper.userHasRole(1L, "ADMIN")).thenReturn(true);

        initializer("a-different-bootstrap-password").run(null);

        verify(passwordEncoder, never()).encode(any());
        verify(userMapper, never()).updateById(any(SysUser.class));
        verify(userRoleMapper, never()).insert(any(com.hqc.smrag.entity.SysUserRole.class));
    }

    private BootstrapAdminInitializer initializer(String password) {
        return new BootstrapAdminInitializer(
            userMapper,
            roleMapper,
            userRoleMapper,
            passwordEncoder,
            "admin",
            password
        );
    }

    private SysUser user(Long id, String passwordHash) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("admin");
        user.setPasswordHash(passwordHash);
        user.setStatus("ACTIVE");
        return user;
    }
}
