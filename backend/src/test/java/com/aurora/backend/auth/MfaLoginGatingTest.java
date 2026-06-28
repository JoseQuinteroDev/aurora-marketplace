package com.aurora.backend.auth;

import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.TestcontainersConfiguration;
import com.aurora.backend.auth.dto.AuthResponse;
import com.aurora.backend.auth.dto.LoginRequest;
import com.aurora.backend.auth.dto.MfaEnrollResponse;
import com.aurora.backend.auth.dto.RegisterRequest;
import com.aurora.backend.auth.dto.VerifyMfaRequest;
import com.aurora.backend.auth.service.AuthService;
import com.aurora.backend.auth.service.MfaService;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.security.mfa.Base32;
import com.aurora.backend.security.mfa.MfaSecretCipher;
import com.aurora.backend.security.mfa.TotpGenerator;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end MFA login-gating against PostgreSQL (OWASP A07), mirroring {@code CheckoutIdempotencyTest}.
 * Drives the real {@link AuthService} + {@link MfaService} + {@code MfaChallengeService} + the
 * Flyway-managed {@code mfa_challenges} table through the full flow:
 *
 * <ol>
 *   <li>register → enroll → confirm enables MFA;</li>
 *   <li>login returns {@code MFA_REQUIRED} with an opaque {@code mfaToken} and NO tokens;</li>
 *   <li>verify with a valid current TOTP returns the real access + refresh tokens;</li>
 *   <li>a SECOND verify with the same {@code mfaToken} fails (single-use challenge).</li>
 * </ol>
 *
 * Requires Docker (Testcontainers).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MfaLoginGatingTest {

    private static final String PASSWORD = "Sup3r-Str0ng-Passphrase!42";

    @Autowired private AuthService authService;
    @Autowired private MfaService mfaService;
    @Autowired private UserRepository userRepository;
    @Autowired private MfaSecretCipher cipher;

    @Test
    void enrollConfirmLoginChallengeAndVerifyIssuesTokens_andTheChallengeIsSingleUse() {
        User user = registerWithMfaEnabled();

        // 1) Login: password is correct but MFA is on → MFA_REQUIRED, NO tokens, only a challenge.
        AuthResponse login = authService.login(new LoginRequest(user.getEmail(), PASSWORD));
        assertThat(login.status()).isEqualTo("MFA_REQUIRED");
        assertThat(login.accessToken()).isNull();
        assertThat(login.refreshToken()).isNull();
        assertThat(login.mfaToken()).isNotBlank();

        // 2) Verify with a valid current TOTP → real tokens.
        String code = currentCodeFor(user.getId());
        AuthResponse verified = authService.verifyMfa(new VerifyMfaRequest(login.mfaToken(), code));
        assertThat(verified.status()).isEqualTo("AUTHENTICATED");
        assertThat(verified.accessToken()).isNotBlank();
        assertThat(verified.refreshToken()).isNotBlank();

        // 3) Single-use: the SAME mfaToken cannot be redeemed again (consumed) → generic 401.
        String codeAgain = currentCodeFor(user.getId());
        assertThatThrownBy(() -> authService.verifyMfa(new VerifyMfaRequest(login.mfaToken(), codeAgain)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("MFA_CHALLENGE_INVALID"));
    }

    @Test
    void verifyWithAWrongCodeDoesNotConsumeTheChallengeButFailsGenerically() {
        User user = registerWithMfaEnabled();
        AuthResponse login = authService.login(new LoginRequest(user.getEmail(), PASSWORD));

        // Wrong code → generic 401 (one bounded attempt charged); no tokens.
        assertThatThrownBy(() -> authService.verifyMfa(new VerifyMfaRequest(login.mfaToken(), "000000")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("MFA_CHALLENGE_INVALID"));

        // The challenge survives a single wrong attempt (under the cap), so a correct code still works.
        String code = currentCodeFor(user.getId());
        AuthResponse verified = authService.verifyMfa(new VerifyMfaRequest(login.mfaToken(), code));
        assertThat(verified.status()).isEqualTo("AUTHENTICATED");
        assertThat(verified.accessToken()).isNotBlank();
    }

    /** Registers a fresh user, enrolls MFA, and confirms it with a real current TOTP code. */
    private User registerWithMfaEnabled() {
        String email = "mfa-" + UUID.randomUUID() + "@aurora.test";
        authService.register(new RegisterRequest(email, PASSWORD, "Mfa", "User", null));
        User user = userRepository.findByEmail(email).orElseThrow();

        MfaEnrollResponse enroll = mfaService.enroll(user);
        // Compute the current code straight from the secret the SPA would have scanned.
        byte[] secret = Base32.decode(enroll.secret());
        mfaService.confirm(user, TotpGenerator.generate(secret, Instant.now()));

        return userRepository.findByEmail(email).orElseThrow();
    }

    /** The current valid TOTP for the (encrypted) secret now stored on the user. */
    private String currentCodeFor(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        byte[] secret = Base32.decode(cipher.decrypt(user.getMfaSecret()));
        return TotpGenerator.generate(secret, Instant.now());
    }
}
