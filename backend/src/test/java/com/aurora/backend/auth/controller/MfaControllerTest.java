package com.aurora.backend.auth.controller;

import com.aurora.backend.auth.dto.MfaStatusResponse;
import com.aurora.backend.auth.service.MfaService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for the MFA self-service endpoints, driven through the real {@link SecurityConfig}
 * filter chain (OWASP A01/A07). Asserts the access-control wiring — the MFA endpoints are NOT public,
 * so an anonymous caller is 401 and an authenticated user reaches the controller — plus the
 * code-format Bean Validation on confirm/disable. Runs as a slice test (no DB, no Docker): the JWT
 * filter is given mocked collaborators so, with no Bearer header, it passes the request through.
 */
@WebMvcTest(controllers = MfaController.class)
@Import({SecurityConfig.class, MfaControllerTest.TestSecurityBeans.class})
class MfaControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private MfaService mfaService;
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
    void statusRequiresAuthenticationForAnonymous() throws Exception {
        mvc.perform(get("/api/auth/mfa/status"))
                .andExpect(status().isUnauthorized());

        verify(mfaService, never()).status(any());
    }

    @Test
    void statusIsReachableForAnAuthenticatedUser() throws Exception {
        when(currentUserService.getCurrentUser(any())).thenReturn(mock(User.class));
        when(mfaService.status(any())).thenReturn(new MfaStatusResponse(false));

        mvc.perform(get("/api/auth/mfa/status").with(user("u").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        verify(mfaService).status(any());
    }

    @Test
    void enrollRequiresAuthenticationForAnonymous() throws Exception {
        mvc.perform(post("/api/auth/mfa/enroll"))
                .andExpect(status().isUnauthorized());

        verify(mfaService, never()).enroll(any());
    }

    @Test
    void confirmRequiresAuthenticationForAnonymous() throws Exception {
        mvc.perform(post("/api/auth/mfa/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized());

        verify(mfaService, never()).confirm(any(), any());
    }

    @Test
    void confirmWithAMalformedCodeIs400AndDoesNotCallTheService() throws Exception {
        when(currentUserService.getCurrentUser(any())).thenReturn(mock(User.class));

        mvc.perform(post("/api/auth/mfa/confirm").with(user("u").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(mfaService, never()).confirm(any(), any());
    }

    @Test
    void confirmWithAValidCodeReachesTheService() throws Exception {
        when(currentUserService.getCurrentUser(any())).thenReturn(mock(User.class));

        mvc.perform(post("/api/auth/mfa/confirm").with(user("u").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("MFA enabled."));

        verify(mfaService).confirm(any(), any());
    }
}
