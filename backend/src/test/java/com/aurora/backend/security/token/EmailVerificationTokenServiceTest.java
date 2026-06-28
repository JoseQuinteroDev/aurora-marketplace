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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailVerificationTokenService}: hashed-at-rest single-use tokens,
 * the two issue variants (in-current-tx for register vs REQUIRES_NEW for resend), and a
 * uniform {@code INVALID_VERIFICATION_TOKEN} for every failure mode.
 */
class EmailVerificationTokenServiceTest {

    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-0000000000dd");

    private final EmailVerificationTokenRepository repository = mock(EmailVerificationTokenRepository.class);
    private final EmailVerificationTokenService service = new EmailVerificationTokenService(repository, 1440L);

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

    private User user() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        return user;
    }

    private EmailVerificationToken row(UUID id, String secret, EmailVerificationTokenStatus status, Instant expiresAt) {
        EmailVerificationToken token = new EmailVerificationToken(id, USER_ID, sha256Hex(secret), expiresAt);
        ReflectionTestUtils.setField(token, "status", status);
        return token;
    }

    private void assertInvalid(String rawToken) {
        assertThatThrownBy(() -> service.consume(rawToken))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_VERIFICATION_TOKEN"));
    }

    @Test
    void issueInCurrentTransactionStoresOnlyTheHashAndDoesNotRevoke() {
        User user = user();

        String raw = service.issueInCurrentTransaction(user);

        // register path: a brand-new user has no prior token, so no revoke is issued.
        verify(repository, never()).revokeActiveForUser(any(), any());
        ArgumentCaptor<EmailVerificationToken> captor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(repository).save(captor.capture());
        String[] parts = raw.split("\\.", 2);
        assertThat(parts[0]).isEqualTo(captor.getValue().getId().toString());
        assertThat(captor.getValue().getTokenHash()).isEqualTo(sha256Hex(parts[1]));
        assertThat(captor.getValue().getStatus()).isEqualTo(EmailVerificationTokenStatus.ACTIVE);
    }

    @Test
    void issueRevokesAnyPriorActiveTokenFirst() {
        service.issue(user());

        verify(repository).revokeActiveForUser(eq(USER_ID), any());
        verify(repository).save(any(EmailVerificationToken.class));
    }

    @Test
    void consumeHappyPathClaimsAndReturnsUserId() {
        UUID rowId = UUID.randomUUID();
        String secret = "the-secret";
        when(repository.findById(rowId))
                .thenReturn(Optional.of(row(rowId, secret, EmailVerificationTokenStatus.ACTIVE, future())));
        when(repository.claimForUse(eq(rowId), any())).thenReturn(1);

        assertThat(service.consume(rowId + "." + secret)).isEqualTo(USER_ID);
        verify(repository).claimForUse(eq(rowId), any());
    }

    @Test
    void consumeRejectsMalformedUnknownWrongSecretExpiredAndReplay() {
        assertInvalid(null);
        assertInvalid("no-dot");
        assertInvalid("not-a-uuid.secret");

        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());
        assertInvalid(unknown + ".secret");

        UUID wrong = UUID.randomUUID();
        when(repository.findById(wrong))
                .thenReturn(Optional.of(row(wrong, "real", EmailVerificationTokenStatus.ACTIVE, future())));
        assertInvalid(wrong + ".bad");

        UUID expired = UUID.randomUUID();
        when(repository.findById(expired)).thenReturn(Optional.of(
                row(expired, "s", EmailVerificationTokenStatus.ACTIVE, Instant.now().minus(1, ChronoUnit.MINUTES))));
        assertInvalid(expired + ".s");

        UUID replay = UUID.randomUUID();
        when(repository.findById(replay))
                .thenReturn(Optional.of(row(replay, "s", EmailVerificationTokenStatus.ACTIVE, future())));
        when(repository.claimForUse(eq(replay), any())).thenReturn(0);
        assertInvalid(replay + ".s");
    }

    @Test
    void burnEquivalentWorkTouchesNoDatabase() {
        service.burnEquivalentWork();
        verifyNoInteractions(repository);
    }

    private static Instant future() {
        return Instant.now().plus(1, ChronoUnit.DAYS);
    }
}
