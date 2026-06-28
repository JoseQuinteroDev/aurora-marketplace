package com.aurora.backend.auth.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.auth.dto.MfaEnrollResponse;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.security.mfa.Base32;
import com.aurora.backend.security.mfa.MfaProperties;
import com.aurora.backend.security.mfa.MfaSecretCipher;
import com.aurora.backend.security.mfa.TotpGenerator;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;
import com.aurora.backend.user.role.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MfaService} (OWASP A07). The crypto primitives ({@link Base32},
 * {@link TotpGenerator}, {@link MfaSecretCipher}) are pure and exercised for real with a test key;
 * only the {@link UserRepository} and {@link AuditLogService} are mocked. A valid code is computed
 * with the real {@link TotpGenerator} against the very secret the service generated (recovered by
 * decrypting what enroll stored on the entity), proving the round-trip end to end.
 */
class MfaServiceTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000aa");
    // A real 32-byte AES key (base64) for the cipher used in these tests.
    private static final byte[] TEST_KEY = "0123456789abcdef0123456789abcdef".getBytes();

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final MfaSecretCipher cipher = new MfaSecretCipher(TEST_KEY);

    private MfaService mfaService;

    @BeforeEach
    void setUp() {
        mfaService = new MfaService(
                userRepository, cipher, auditLogService,
                new MfaProperties("unused-in-service", "Aurora Marketplace"));
    }

    private User newUser() {
        User user = new User("ada@aurora.test", "hash", "Ada", "Lovelace", Role.CUSTOMER, true);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private void givenUser(User user) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    /** The current valid TOTP for whatever (encrypted) secret is now stored on the user. */
    private String currentCodeFor(User user) {
        byte[] secret = Base32.decode(cipher.decrypt(user.getMfaSecret()));
        return TotpGenerator.generate(secret, Instant.now());
    }

    @Test
    void enrollStoresAnEncryptedPendingSecretAndDoesNotEnable() {
        User user = newUser();
        givenUser(user);

        MfaEnrollResponse response = mfaService.enroll(user);

        // Returned to the user once, in the clear, for QR/manual entry.
        assertThat(response.secret()).isNotBlank();
        assertThat(Base32.decode(response.secret())).hasSize(20); // 160-bit secret

        // Stored ciphertext is NOT the plaintext secret, but decrypts back to it.
        assertThat(user.getMfaSecret()).isNotNull().isNotEqualTo(response.secret());
        assertThat(cipher.decrypt(user.getMfaSecret())).isEqualTo(response.secret());

        // Pending only — not enabled yet.
        assertThat(user.isMfaEnabled()).isFalse();
        assertThat(user.getMfaEnrolledAt()).isNull();
    }

    @Test
    void enrollProducesAWellFormedOtpauthUri() {
        User user = newUser();
        givenUser(user);

        MfaEnrollResponse response = mfaService.enroll(user);

        assertThat(response.otpauthUri())
                .startsWith("otpauth://totp/Aurora%20Marketplace:ada%40aurora.test?")
                .contains("secret=" + response.secret())
                .contains("issuer=Aurora%20Marketplace")
                .contains("algorithm=SHA1")
                .contains("digits=6")
                .contains("period=30");
    }

    @Test
    void enrollIsRejectedWhenAlreadyEnabled() {
        User user = newUser();
        user.beginMfaEnrollment(cipher.encrypt("JBSWY3DPEHPK3PXP"));
        user.enableMfa(Instant.now());
        givenUser(user);

        assertThatThrownBy(() -> mfaService.enroll(user))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("MFA_ALREADY_ENABLED"));
    }

    @Test
    void reEnrollOverwritesThePendingSecret() {
        User user = newUser();
        givenUser(user);

        MfaEnrollResponse first = mfaService.enroll(user);
        MfaEnrollResponse second = mfaService.enroll(user);

        assertThat(second.secret()).isNotEqualTo(first.secret());
        assertThat(cipher.decrypt(user.getMfaSecret())).isEqualTo(second.secret());
        assertThat(user.isMfaEnabled()).isFalse();
    }

    @Test
    void confirmEnablesMfaOnAValidCodeAndAudits() {
        User user = newUser();
        givenUser(user);
        mfaService.enroll(user);

        mfaService.confirm(user, currentCodeFor(user));

        assertThat(user.isMfaEnabled()).isTrue();
        assertThat(user.getMfaEnrolledAt()).isNotNull();
        verify(auditLogService).log(eq(AuditEventType.MFA_ENABLED), eq(user), eq("USER"), eq(USER_ID), any());
    }

    @Test
    void confirmDoesNotEnableOnAWrongCode() {
        User user = newUser();
        givenUser(user);
        mfaService.enroll(user);

        assertThatThrownBy(() -> mfaService.confirm(user, "000000"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("MFA_INVALID_CODE"));

        assertThat(user.isMfaEnabled()).isFalse();
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void confirmIsRejectedWhenNoPendingSecret() {
        User user = newUser(); // never enrolled
        givenUser(user);

        assertThatThrownBy(() -> mfaService.confirm(user, "123456"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("MFA_INVALID_CODE"));

        assertThat(user.isMfaEnabled()).isFalse();
    }

    @Test
    void disableRequiresAValidCodeAndClearsTheSecret() {
        User user = newUser();
        givenUser(user);
        mfaService.enroll(user);
        mfaService.confirm(user, currentCodeFor(user));

        mfaService.disable(user, currentCodeFor(user));

        assertThat(user.isMfaEnabled()).isFalse();
        assertThat(user.getMfaSecret()).isNull();
        assertThat(user.getMfaEnrolledAt()).isNull();
        verify(auditLogService).log(eq(AuditEventType.MFA_DISABLED), eq(user), eq("USER"), eq(USER_ID), any());
    }

    @Test
    void disableIsRejectedOnAWrongCodeAndKeepsMfaEnabled() {
        User user = newUser();
        givenUser(user);
        mfaService.enroll(user);
        mfaService.confirm(user, currentCodeFor(user));

        assertThatThrownBy(() -> mfaService.disable(user, "000000"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("MFA_INVALID_CODE"));

        assertThat(user.isMfaEnabled()).isTrue();
        assertThat(user.getMfaSecret()).isNotNull();
    }

    @Test
    void disableIsRejectedWhenMfaIsNotEnabled() {
        User user = newUser();
        givenUser(user);

        assertThatThrownBy(() -> mfaService.disable(user, "123456"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("MFA_NOT_ENABLED"));
    }

    @Test
    void statusReflectsTheEnabledFlag() {
        User user = newUser();
        givenUser(user);

        assertThat(mfaService.status(user).enabled()).isFalse();

        mfaService.enroll(user);
        mfaService.confirm(user, currentCodeFor(user));
        assertThat(mfaService.status(user).enabled()).isTrue();
    }
}
