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
import com.aurora.backend.auth.dto.ResendVerificationRequest;
import com.aurora.backend.auth.dto.ResetPasswordRequest;
import com.aurora.backend.auth.dto.VerifyEmailRequest;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.messaging.AuroraTopics;
import com.aurora.backend.messaging.event.EmailVerificationRequestedEvent;
import com.aurora.backend.messaging.event.PasswordResetRequestedEvent;
import com.aurora.backend.messaging.outbox.OutboxEventRecorder;
import com.aurora.backend.security.jwt.JwtService;
import com.aurora.backend.security.password.PasswordBreachChecker;
import com.aurora.backend.security.token.EmailVerificationTokenService;
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
import org.springframework.test.util.ReflectionTestUtils;

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
    private final EmailVerificationTokenService emailVerificationTokenService = mock(EmailVerificationTokenService.class);
    private final OutboxEventRecorder outboxRecorder = mock(OutboxEventRecorder.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final RefreshTokenReuseResponder reuseResponder = mock(RefreshTokenReuseResponder.class);
    private final PasswordBreachChecker passwordBreachChecker = mock(PasswordBreachChecker.class);

    private final AuthService authService = new AuthService(
            userRepository, passwordEncoder, authenticationManager, jwtService,
            loginAttemptService, tokenDenylistService, auditLogService, refreshTokenService,
            passwordResetTokenService, emailVerificationTokenService, outboxRecorder,
            refreshTokenRepository, reuseResponder, passwordBreachChecker,
            30, 60L, 0L,        // password-reset: expiry, interval, latency-floor (0 = no sleep in tests)
            1440, 60L, 0L);     // email-verification: expiry, interval, latency-floor

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
        // Give the saved user an id (register's issueVerification reads it); the captor still
        // sees the same instance, so phone/email assertions are unaffected.
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", USER_ID);
            return saved;
        });
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

    // --- email verification ---

    @Test
    void registerIssuesAVerificationTokenAndRecordsTheEvent() {
        stubHappyPath();
        when(emailVerificationTokenService.issueInCurrentTransaction(any(User.class))).thenReturn("ev.secret");

        authService.register(new RegisterRequest("ada@aurora.test", "password123", "Ada", "Lovelace", null));

        // Uses the in-current-transaction variant (FK-safe), NOT the REQUIRES_NEW issue().
        verify(emailVerificationTokenService).issueInCurrentTransaction(any(User.class));
        verify(emailVerificationTokenService, never()).issue(any());
        verify(outboxRecorder).record(eq("USER"), any(), eq("EMAIL_VERIFICATION_REQUESTED"),
                eq(AuroraTopics.EMAIL_VERIFICATION_REQUESTED), any(), any());
    }

    @Test
    void verifyEmailFlipsTheFlagOnceAndAuditsWithoutRevokingSessions() {
        User user = mockUser(true);
        when(user.isEmailVerified()).thenReturn(false);
        when(emailVerificationTokenService.consume("ev.secret")).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        authService.verifyEmail(new VerifyEmailRequest("ev.secret"));

        verify(user).verifyEmail();
        verify(userRepository).save(user);
        verify(auditLogService).log(eq(AuditEventType.EMAIL_VERIFIED), eq(user), any(), eq(USER_ID), any());
        // Verification is NOT a credential change — sessions stay intact (unlike password reset).
        verify(refreshTokenRepository, never()).findDistinctFamilyIdByUserId(any());
    }

    @Test
    void verifyEmailIsANoOpSuccessWhenAlreadyVerified() {
        User user = mockUser(true);
        when(user.isEmailVerified()).thenReturn(true);
        when(emailVerificationTokenService.consume("ev.secret")).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        authService.verifyEmail(new VerifyEmailRequest("ev.secret"));

        verify(user, never()).verifyEmail();
        verify(userRepository, never()).save(any());
    }

    @Test
    void resendVerificationForAnAlreadyVerifiedAccountBurnsAndIssuesNothing() {
        User verified = mockUser(true);
        when(verified.isEmailVerified()).thenReturn(true);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(verified));

        authService.resendVerification(new ResendVerificationRequest("ada@aurora.test"));

        verify(emailVerificationTokenService).burnEquivalentWork();
        verify(emailVerificationTokenService, never()).issue(any());
        verifyNoInteractions(outboxRecorder);
    }

    @Test
    void resendVerificationForAnUnverifiedAccountIssuesAndRecords() {
        User unverified = mockUser(true);
        when(unverified.isEmailVerified()).thenReturn(false);
        when(unverified.getEmail()).thenReturn("ada@aurora.test");
        when(unverified.getFirstName()).thenReturn("Ada");
        when(userRepository.findByEmail("ada@aurora.test")).thenReturn(Optional.of(unverified));
        when(emailVerificationTokenService.issue(unverified)).thenReturn("ev.secret");

        authService.resendVerification(new ResendVerificationRequest("ada@aurora.test"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxRecorder).record(eq("USER"), eq(USER_ID.toString()), eq("EMAIL_VERIFICATION_REQUESTED"),
                eq(AuroraTopics.EMAIL_VERIFICATION_REQUESTED), eq(USER_ID.toString()), payload.capture());
        assertThat(((EmailVerificationRequestedEvent) payload.getValue()).verificationToken()).isEqualTo("ev.secret");
        verify(auditLogService).log(eq(AuditEventType.EMAIL_VERIFICATION_REQUESTED), eq(unverified), any(), any(), any());
    }

    // --- breached-password check (OWASP A07 credential hygiene) ---

    @Test
    void registerWithABreachedPasswordIsRejectedAndPersistsNothing() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordBreachChecker.isBreached("password123")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "ada@aurora.test", "password123", "Ada", "Lovelace", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("PASSWORD_BREACHED"));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(outboxRecorder);
    }

    @Test
    void resetPasswordWithABreachedPasswordIsRejectedWithoutConsumingTheToken() {
        when(passwordBreachChecker.isBreached("Password123!")).thenReturn(true);

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("rid.secret", "Password123!")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("PASSWORD_BREACHED"));

        // The breach check precedes token consumption, so a valid reset link is not burned.
        verify(passwordResetTokenService, never()).consume(any());
        verify(userRepository, never()).save(any());
        verify(reuseResponder, never()).revokeFamily(any());
    }
}
