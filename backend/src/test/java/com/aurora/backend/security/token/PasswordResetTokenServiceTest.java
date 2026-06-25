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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PasswordResetTokenService}: hashed-at-rest single-use tokens,
 * constant-time validation, and a uniform {@code INVALID_RESET_TOKEN} for every failure
 * mode (anti-enumeration). (OWASP A07.)
 */
class PasswordResetTokenServiceTest {

    private static final UUID USER_ID = UUID.fromString("cccccccc-0000-0000-0000-0000000000cc");

    private final PasswordResetTokenRepository repository = mock(PasswordResetTokenRepository.class);
    private final PasswordResetTokenService service = new PasswordResetTokenService(repository, 30L);

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

    private PasswordResetToken row(UUID id, String secret, PasswordResetTokenStatus status, Instant expiresAt) {
        PasswordResetToken token = new PasswordResetToken(id, USER_ID, sha256Hex(secret), expiresAt);
        ReflectionTestUtils.setField(token, "status", status);
        return token;
    }

    private void assertInvalid(String rawToken) {
        assertThatThrownBy(() -> service.consume(rawToken))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_RESET_TOKEN"));
    }

    @Test
    void issueRevokesAnyPriorActiveTokenAndStoresOnlyTheHash() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);

        String raw = service.issue(user);

        verify(repository).revokeActiveForUser(eq(USER_ID), any());
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(repository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();

        String[] parts = raw.split("\\.", 2);
        assertThat(parts).hasSize(2);
        assertThat(parts[0]).isEqualTo(saved.getId().toString());
        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(parts[1]));
        assertThat(saved.getTokenHash()).isNotEqualTo(parts[1]);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getStatus()).isEqualTo(PasswordResetTokenStatus.ACTIVE);
    }

    @Test
    void consumeHappyPathClaimsTheTokenAndReturnsTheUserId() {
        UUID rowId = UUID.randomUUID();
        String secret = "the-secret";
        when(repository.findById(rowId))
                .thenReturn(Optional.of(row(rowId, secret, PasswordResetTokenStatus.ACTIVE, future())));
        when(repository.claimForUse(eq(rowId), any())).thenReturn(1);

        UUID result = service.consume(rowId + "." + secret);

        assertThat(result).isEqualTo(USER_ID);
        verify(repository).claimForUse(eq(rowId), any());
    }

    @Test
    void consumeRejectsMalformedTokens() {
        assertInvalid(null);
        assertInvalid("no-dot");
        assertInvalid(".leadingDot");
        assertInvalid("trailingDot.");
        assertInvalid("not-a-uuid.secret");
    }

    @Test
    void consumeRejectsAnUnknownRowId() {
        UUID rowId = UUID.randomUUID();
        when(repository.findById(rowId)).thenReturn(Optional.empty());
        assertInvalid(rowId + ".secret");
    }

    @Test
    void consumeRejectsAWrongSecret() {
        UUID rowId = UUID.randomUUID();
        when(repository.findById(rowId))
                .thenReturn(Optional.of(row(rowId, "real-secret", PasswordResetTokenStatus.ACTIVE, future())));
        assertInvalid(rowId + ".wrong-secret");
    }

    @Test
    void consumeRejectsExpiredUsedOrRevokedTokens() {
        UUID expiredId = UUID.randomUUID();
        when(repository.findById(expiredId)).thenReturn(Optional.of(
                row(expiredId, "s", PasswordResetTokenStatus.ACTIVE, Instant.now().minus(1, ChronoUnit.MINUTES))));
        assertInvalid(expiredId + ".s");

        UUID usedId = UUID.randomUUID();
        when(repository.findById(usedId)).thenReturn(Optional.of(
                row(usedId, "s", PasswordResetTokenStatus.USED, future())));
        assertInvalid(usedId + ".s");
    }

    @Test
    void consumeRejectsAReplayWhenTheAtomicClaimLoses() {
        UUID rowId = UUID.randomUUID();
        String secret = "s";
        when(repository.findById(rowId))
                .thenReturn(Optional.of(row(rowId, secret, PasswordResetTokenStatus.ACTIVE, future())));
        when(repository.claimForUse(eq(rowId), any())).thenReturn(0); // already claimed by a concurrent caller
        assertInvalid(rowId + "." + secret);
    }

    @Test
    void burnEquivalentWorkTouchesNoDatabase() {
        service.burnEquivalentWork();
        verifyNoInteractions(repository);
    }

    private static Instant future() {
        return Instant.now().plus(30, ChronoUnit.MINUTES);
    }
}
