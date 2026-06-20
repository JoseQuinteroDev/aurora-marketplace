package com.aurora.backend.auth.service;

import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.auth.dto.RegisterRequest;
import com.aurora.backend.security.jwt.JwtService;
import com.aurora.backend.security.token.TokenDenylistService;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for registration. The headline assertion is that the optional phone
 * captured at sign-up is actually persisted — it is what the order-SMS channel
 * depends on (OWASP-unrelated, but a regression guard for a feature that was
 * silently dropping the value before).
 */
class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final TokenDenylistService tokenDenylistService = mock(TokenDenylistService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final AuthService authService = new AuthService(
            userRepository, passwordEncoder, authenticationManager, jwtService,
            loginAttemptService, tokenDenylistService, auditLogService);

    private void stubHappyPath() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("token");
        when(jwtService.getExpirationMinutes()).thenReturn(60L);
    }

    @Test
    void registerPersistsTheProvidedPhone() {
        stubHappyPath();

        authService.register(new RegisterRequest(
                "Ada@Aurora.test", "password123", "Ada", "Lovelace", "+34 600 123 456"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPhone()).isEqualTo("+34 600 123 456");
        assertThat(captor.getValue().getEmail()).isEqualTo("ada@aurora.test"); // normalized
    }

    @Test
    void registerStoresABlankPhoneAsNull() {
        stubHappyPath();

        authService.register(new RegisterRequest(
                "ada@aurora.test", "password123", "Ada", "Lovelace", "   "));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPhone()).isNull();
    }
}
