package com.aurora.backend.auth.service;

import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.auth.dto.AuthResponse;
import com.aurora.backend.auth.dto.RefreshRequest;
import com.aurora.backend.auth.dto.RegisterRequest;
import com.aurora.backend.security.jwt.JwtService;
import com.aurora.backend.security.token.RefreshTokenService;
import com.aurora.backend.security.token.TokenDenylistService;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;
import com.aurora.backend.user.role.Role;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}: phone persistence at registration, that both
 * tokens are issued, refresh delegates to rotation, and logout revokes the
 * refresh-token family only when one is supplied (OWASP A07).
 */
class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final TokenDenylistService tokenDenylistService = mock(TokenDenylistService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);

    private final AuthService authService = new AuthService(
            userRepository, passwordEncoder, authenticationManager, jwtService,
            loginAttemptService, tokenDenylistService, auditLogService, refreshTokenService);

    private User user() {
        return new User("ada@aurora.test", "hash", "Ada", "Lovelace", Role.CUSTOMER, true);
    }

    private void stubHappyPath() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("token");
        when(jwtService.getExpirationMinutes()).thenReturn(15L);
        when(refreshTokenService.issue(any(User.class), any())).thenReturn("rid.secret");
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

    @Test
    void registerIssuesBothAccessAndRefreshTokens() {
        stubHappyPath();

        AuthResponse response = authService.register(new RegisterRequest(
                "ada@aurora.test", "password123", "Ada", "Lovelace", null));

        assertThat(response.accessToken()).isEqualTo("token");
        assertThat(response.refreshToken()).isEqualTo("rid.secret");
        verify(refreshTokenService).issue(any(User.class), any());
    }

    @Test
    void refreshDelegatesToRotationAndReturnsTheRotatedTokens() {
        when(jwtService.getExpirationMinutes()).thenReturn(15L);
        when(refreshTokenService.rotate("rid.secret"))
                .thenReturn(new RefreshTokenService.RotationResult(user(), "newAccess", "newRid.secret"));

        AuthResponse response = authService.refresh(new RefreshRequest("rid.secret"));

        assertThat(response.accessToken()).isEqualTo("newAccess");
        assertThat(response.refreshToken()).isEqualTo("newRid.secret");
        assertThat(response.user()).isNotNull();
    }

    @Test
    void logoutWithARefreshTokenRevokesTheFamilyAndAudits() {
        User actor = user();

        authService.logout(null, actor, "rid.secret");

        verify(refreshTokenService).revokeFamilyOf("rid.secret");
        verify(auditLogService).log(eq(AuditEventType.LOGOUT), eq(actor), any(), any(), any());
    }

    @Test
    void logoutWithoutARefreshTokenLeavesTheFamilyUntouched() {
        authService.logout(null, user(), null);

        verify(refreshTokenService, never()).revokeFamilyOf(any());
    }
}
