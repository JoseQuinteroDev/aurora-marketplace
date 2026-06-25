package com.aurora.backend.auth.controller;

import com.aurora.backend.auth.service.AuthService;
import com.aurora.backend.config.SecurityConfig;
import com.aurora.backend.security.CurrentUserService;
import com.aurora.backend.security.jwt.JwtAuthenticationFilter;
import com.aurora.backend.security.jwt.JwtService;
import com.aurora.backend.security.token.TokenDenylistService;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Input-validation tests for the public auth endpoints (OWASP A03 / hardening):
 * malformed or invalid bodies return a clean {@code 400 VALIDATION_ERROR} via
 * {@link com.aurora.backend.common.exception.GlobalExceptionHandler} — never a
 * {@code 500}, and the service is never invoked with bad input.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, AuthValidationTest.TestSecurityBeans.class})
class AuthValidationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private AuthService authService;
    @MockitoBean private CurrentUserService currentUserService;
    @MockitoBean private UserRepository userRepository;   // backs SecurityConfig#userDetailsService

    static class TestSecurityBeans {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(
                    mock(JwtService.class),
                    mock(UserDetailsService.class),
                    mock(TokenDenylistService.class));
        }
    }

    @Test
    void registerWithBlankFieldsReturns400AndDoesNotCallTheService() throws Exception {
        String body = """
                {"email":"","password":"","firstName":"","lastName":""}
                """;

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(authService, never()).register(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registerWithInvalidEmailAndShortPasswordReturns400() throws Exception {
        String body = """
                {"email":"not-an-email","password":"short","firstName":"Ann","lastName":"Lee"}
                """;

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void malformedJsonReturnsCleanBadRequestNot500() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void registerWithValidBodyReachesTheService() throws Exception {
        String body = """
                {"email":"valid@aurora.test","password":"Password123!","firstName":"Ann","lastName":"Lee"}
                """;

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        verify(authService).register(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshIsPublicAndReachesTheServiceWithAValidBody() throws Exception {
        // permitAll: an anonymous, well-formed refresh reaches the controller (not 401 from the chain).
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"rid.secret\"}"))
                .andExpect(status().isOk());

        verify(authService).refresh(any());
    }

    @Test
    void refreshWithABlankTokenIs400AndDoesNotCallTheService() throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(authService, never()).refresh(any());
    }

    @Test
    void revokeIsPublicAndAlwaysSucceeds() throws Exception {
        // permitAll + anti-enumeration: anonymous, well-formed revoke returns 200.
        mvc.perform(post("/api/auth/revoke").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"rid.secret\"}"))
                .andExpect(status().isOk());

        verify(authService).revoke(any());
    }

    @Test
    void logoutStillRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutPassesTheRefreshTokenThroughForAnAuthenticatedUser() throws Exception {
        when(currentUserService.getCurrentUser(any())).thenReturn(mock(User.class));

        mvc.perform(post("/api/auth/logout").with(user("u").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"rid.secret\"}"))
                .andExpect(status().isOk());

        verify(authService).logout(any(), any(), eq("rid.secret"));
    }
}
