package com.aurora.backend.auth.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.auth.dto.AuthResponse;
import com.aurora.backend.auth.dto.ForgotPasswordRequest;
import com.aurora.backend.auth.dto.RefreshRequest;
import com.aurora.backend.auth.dto.RegisterRequest;
import com.aurora.backend.auth.dto.ResetPasswordRequest;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.messaging.AuroraTopics;
import com.aurora.backend.messaging.event.PasswordResetRequestedEvent;
import com.aurora.backend.messaging.outbox.OutboxEventRecorder;
import com.aurora.backend.security.jwt.JwtService;
import com.aurora.backend.security.token.PasswordResetTokenService;
import com.aurora.backend.security.token.RefreshTokenRepository;
import com.aurora.backend.security.token.RefreshTokenReuseResponder;
import com.aurora.backend.security.token.RefreshTokenService;
import com.aurora.backend.security.token.TokenDenylistService;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;
import com.aurora.backend.user.role.Role;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}: registration phone persistence, dual-token
 * issuance, refresh/logout delegation, and the password-reset flow — anti-enumeration
 * on request, and full session invalidation on completion (OWASP A07).
 */
class AuthServiceTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000aa");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final TokenDenylistService tokenDenylistService = mock(TokenDenylistService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final PasswordResetTokenService passwordResetTokenService = mock(PasswordResetTokenService.class);
    private final OutboxEventRecorder outboxRecorder = mock(OutboxEventRecorder.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final RefreshTokenReuseResponder reuseResponder = mock(RefreshTokenReuseResponder.class);

    private final AuthService authService = new AuthService(
            userRepository, passwordEncoder, authenticationManager, jwtService,
            loginAttemptService, tokenDenylistService, auditLogService, refreshTokenService,
            passwordResetTokenService, outboxRecorder, refreshTokenRepository, reuseResponder,
            30, 60L);

    private User user() {
        return new User("ada@aurora.test", "hash", "Ada", "Lovelace", Role.CUSTOMER, true);
    }

    private User mockUser(boolean enabled) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.isEnabled()).thenReturn(enabled);
        return user;
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

    @Test
    void revokeDelegatesToTheRefreshTokenFamilyRevoke() {
        authService.revoke(new RefreshRequest("rid.secret"));

        verify(refreshTokenService).revokeFamilyOf("rid.secret");
    }

    // --- password reset ---

    @Test
    void forgotPasswordForAnUnknownEmailDoesEquivalentWorkButIssuesNothing() {
        when(userRepository.findByEmail("ghost@aurora.test")).thenReturn(Optional.empty());

        authService.requestPasswordReset(new ForgotPasswordRequest("ghost@aurora.test"));

        verify(passwordResetTokenService).burnEquivalentWork();
        verify(passwordResetTokenService, never()).issue(any());
        verifyNoInteractions(outboxRecorder);
    }

    @Test
    void forgotPasswordForADisabledAccountIssuesNothing() {
        User disabled = mockUser(false);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(disabled));

        authService.requestPasswordReset(new ForgotPasswordRequest("ada@aurora.test"));

        verify(passwordResetTokenService).burnEquivalentWork();
        verify(passwordResetTokenService, never()).issue(any());
        verifyNoInteractions(outboxRecorder);
    }

    @Test
    void forgotPasswordForAnEnabledAccountIssuesATokenAndRecordsTheEvent() {
        User enabled = mockUser(true);
        when(enabled.getEmail()).thenReturn("ada@aurora.test");
        when(enabled.getFirstName()).thenReturn("Ada");
        when(userRepository.findByEmail("ada@aurora.test")).thenReturn(Optional.of(enabled));
        when(passwordResetTokenService.issue(enabled)).thenReturn("rid.secret");

        authService.requestPasswordReset(new ForgotPasswordRequest("ada@aurora.test"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxRecorder).record(eq("USER"), eq(USER_ID.toString()), eq("PASSWORD_RESET_REQUESTED"),
                eq(AuroraTopics.PASSWORD_RESET_REQUESTED), eq(USER_ID.toString()), payload.capture());
        PasswordResetRequestedEvent event = (PasswordResetRequestedEvent) payload.getValue();
        assertThat(event.resetToken()).isEqualTo("rid.secret");
        assertThat(event.expiresInMinutes()).isEqualTo(30);
        verify(auditLogService).log(eq(AuditEventType.PASSWORD_RESET_REQUESTED), eq(enabled), any(), any(), any());
    }

    @Test
    void forgotPasswordSwallowsAConcurrentTokenRace() {
        User enabled = mockUser(true);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(enabled));
        when(passwordResetTokenService.issue(enabled))
                .thenThrow(new DataIntegrityViolationException("duplicate active token"));

        assertThatCode(() -> authService.requestPasswordReset(new ForgotPasswordRequest("ada@aurora.test")))
                .doesNotThrowAnyException();
        verifyNoInteractions(outboxRecorder);
    }

    @Test
    void resetPasswordRehashesAndInvalidatesEverySession() {
        UUID famA = UUID.randomUUID();
        UUID famB = UUID.randomUUID();
        User enabled = mockUser(true);
        when(passwordResetTokenService.consume("rid.secret")).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(enabled));
        when(passwordEncoder.encode("Password123!")).thenReturn("new-hash");
        when(refreshTokenRepository.findDistinctFamilyIdByUserId(USER_ID)).thenReturn(List.of(famA, famB));

        authService.resetPassword(new ResetPasswordRequest("rid.secret", "Password123!"));

        verify(enabled).changePassword("new-hash");
        verify(userRepository).save(enabled);
        verify(reuseResponder).revokeFamily(famA);
        verify(reuseResponder).revokeFamily(famB);
        verify(auditLogService).log(eq(AuditEventType.PASSWORD_RESET), eq(enabled), any(), eq(USER_ID), any());
    }

    @Test
    void resetPasswordForADisabledUserFailsClosedWithoutTouchingSessions() {
        User disabled = mockUser(false);
        when(passwordResetTokenService.consume("rid.secret")).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("rid.secret", "Password123!")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_RESET_TOKEN"));

        verify(userRepository, never()).save(any());
        verify(reuseResponder, never()).revokeFamily(any());
    }
}
