package com.aurora.backend.security.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.user.entity.User;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MfaChallengeService} (OWASP A07): hashed-at-rest single-use login
 * challenges, constant-time validation, single-use consumption via the atomic claim, the bounded
 * attempt cap, and a uniform {@code MFA_CHALLENGE_INVALID} for every failure mode (the
 * challenge-not-found and wrong-secret responses are identical — anti-enumeration).
 */
class MfaChallengeServiceTest {

    private static final UUID USER_ID = UUID.fromString("eeeeeeee-0000-0000-0000-0000000000ee");
    private static final int MAX_ATTEMPTS = 5;

    private final MfaChallengeRepository repository = mock(MfaChallengeRepository.class);
    private final MfaChallengeService service = new MfaChallengeService(repository, 5L, MAX_ATTEMPTS);

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b & 0xFF));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private MfaChallenge row(UUID id, String secret, Instant expiresAt, Instant consumedAt, int attempts) {
        MfaChallenge challenge = new MfaChallenge(id, USER_ID, sha256Hex(secret), expiresAt);
        ReflectionTestUtils.setField(challenge, "consumedAt", consumedAt);
        ReflectionTestUtils.setField(challenge, "attempts", attempts);
        return challenge;
    }

    private void assertInvalid(String rawToken) {
        assertThatThrownBy(() -> service.validate(rawToken))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("MFA_CHALLENGE_INVALID"));
    }

    @Test
    void issueStoresOnlyTheHashAndReturnsTheRawRowIdDotSecret() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);

        String raw = service.issue(user);

        ArgumentCaptor<MfaChallenge> captor = ArgumentCaptor.forClass(MfaChallenge.class);
        verify(repository).save(captor.capture());
        MfaChallenge saved = captor.getValue();

        String[] parts = raw.split("\\.", 2);
        assertThat(parts).hasSize(2);
        assertThat(parts[0]).isEqualTo(saved.getId().toString());
        // The raw secret is NEVER stored — only its SHA-256 hash.
        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(parts[1]));
        assertThat(saved.getTokenHash()).isNotEqualTo(parts[1]);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getConsumedAt()).isNull();
    }

    @Test
    void validateHappyPathReturnsTheChallengeAndUserIdWithoutConsuming() {
        UUID rowId = UUID.randomUUID();
        String secret = "the-secret";
        when(repository.findById(rowId))
                .thenReturn(Optional.of(row(rowId, secret, future(), null, 0)));

        MfaChallengeService.ValidChallenge valid = service.validate(rowId + "." + secret);

        assertThat(valid.challengeId()).isEqualTo(rowId);
        assertThat(valid.userId()).isEqualTo(USER_ID);
        // validate() never mutates — consumption is a separate explicit step.
        verify(repository, org.mockito.Mockito.never()).claimForUse(any(), any());
    }

    @Test
    void validateRejectsMalformedTokens() {
        assertInvalid(null);
        assertInvalid("no-dot");
        assertInvalid(".leadingDot");
        assertInvalid("trailingDot.");
        assertInvalid("not-a-uuid.secret");
    }

    @Test
    void validateRejectsAnUnknownRowId() {
        UUID rowId = UUID.randomUUID();
        when(repository.findById(rowId)).thenReturn(Optional.empty());
        assertInvalid(rowId + ".secret");
    }

    @Test
    void validateRejectsAWrongSecret() {
        UUID rowId = UUID.randomUUID();
        when(repository.findById(rowId))
                .thenReturn(Optional.of(row(rowId, "real-secret", future(), null, 0)));
        assertInvalid(rowId + ".wrong-secret");
    }

    @Test
    void validateRejectsAnExpiredChallenge() {
        UUID rowId = UUID.randomUUID();
        when(repository.findById(rowId)).thenReturn(Optional.of(
                row(rowId, "s", Instant.now().minus(1, ChronoUnit.MINUTES), null, 0)));
        assertInvalid(rowId + ".s");
    }

    @Test
    void validateRejectsAnAlreadyConsumedChallenge() {
        UUID rowId = UUID.randomUUID();
        when(repository.findById(rowId)).thenReturn(Optional.of(
                row(rowId, "s", future(), Instant.now(), 1)));
        assertInvalid(rowId + ".s");
    }

    @Test
    void validateRejectsAChallengeAtOrOverTheAttemptCap() {
        UUID rowId = UUID.randomUUID();
        when(repository.findById(rowId)).thenReturn(Optional.of(
                row(rowId, "s", future(), null, MAX_ATTEMPTS)));
        assertInvalid(rowId + ".s");
    }

    @Test
    void consumeSucceedsWhenTheAtomicClaimWins() {
        UUID rowId = UUID.randomUUID();
        when(repository.claimForUse(eq(rowId), any())).thenReturn(1);

        assertThatCode(() -> service.consume(rowId)).doesNotThrowAnyException();
        verify(repository).claimForUse(eq(rowId), any());
    }

    @Test
    void consumeFailsGenericallyWhenTheAtomicClaimLoses() {
        // Single-use: a second consume (or a concurrent/expired race) loses the conditional UPDATE.
        UUID rowId = UUID.randomUUID();
        when(repository.claimForUse(eq(rowId), any())).thenReturn(0);

        assertThatThrownBy(() -> service.consume(rowId))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("MFA_CHALLENGE_INVALID"));
    }

    @Test
    void recordFailedAttemptDelegatesWithTheConfiguredCap() {
        UUID rowId = UUID.randomUUID();

        service.recordFailedAttempt(rowId);

        verify(repository).recordFailedAttempt(eq(rowId), eq(MAX_ATTEMPTS), any());
    }

    private static Instant future() {
        return Instant.now().plus(5, ChronoUnit.MINUTES);
    }
}
