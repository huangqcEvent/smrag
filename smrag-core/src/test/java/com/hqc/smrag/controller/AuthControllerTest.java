package com.hqc.smrag.controller;

import com.hqc.smrag.security.service.AuthService;
import com.hqc.smrag.service.UserService;
import com.hqc.smrag.vo.UserVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void currentUserReturnsRolesForAuthenticatedUser() {
        UserService userService = mock(UserService.class);
        UserVO admin = UserVO.builder()
            .id(7L)
            .username("admin")
            .roles(List.of("ADMIN"))
            .build();
        when(userService.getUserById(7L)).thenReturn(admin);
        AuthController controller =
            new AuthController(mock(AuthService.class), userService);

        UserVO result = controller.getCurrentUser(7L).getData();

        assertThat(result).isEqualTo(admin);
        assertThat(result.getRoles()).containsExactly("ADMIN");
    }
}
