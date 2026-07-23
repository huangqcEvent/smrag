package com.hqc.smrag.security.filter;

import com.hqc.smrag.mapper.SysUserRoleMapper;
import com.hqc.smrag.security.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usesCurrentDatabaseRolesInsteadOfStaleJwtClaims() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        SysUserRoleMapper roleMapper = mock(SysUserRoleMapper.class);
        when(jwtService.validateToken("token")).thenReturn(true);
        when(jwtService.getUserId("token")).thenReturn(9L);
        when(jwtService.getUsername("token")).thenReturn("judy");
        when(roleMapper.selectRoleCodesByUserId(9L)).thenReturn(List.of("USER"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        new JwtAuthenticationFilter(jwtService, roleMapper).doFilter(
            request,
            new MockHttpServletResponse(),
            new MockFilterChain()
        );

        assertThat(SecurityContextHolder.getContext()
            .getAuthentication().getAuthorities())
            .extracting(Object::toString)
            .containsExactly("ROLE_USER");
        verify(jwtService, never()).getRoles("token");
    }
}
