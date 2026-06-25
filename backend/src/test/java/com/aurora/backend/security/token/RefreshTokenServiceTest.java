package com.aurora.backend.security.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.security.jwt.JwtService;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;

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
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RefreshTokenService} — the heart of refresh-token rotation
 * (OWASP A07). Covers: opaque-token issuance with SHA-256-at-rest, single-use
 * rotation, anti-enumeration rejections, the benign double-submit grace path (must
 * NOT log a legitimate user out), and that reuse/logout delegate the family-wide
 * response to {@link RefreshTokenReuseResponder} (whose own transaction survives the
 * 401 — see RefreshTokenReuseResponderTest).
 */
class RefreshTokenServiceTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID FAMILY_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final RefreshTokenReuseResponder reuseResponder = mock(RefreshTokenReuseResponder.class);

    private final RefreshTokenService service = new RefreshTokenService(
            repository, userRepository, jwtService, reuseResponder, 30L, 10L);

    private User user() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        return user;
    }

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

    private RefreshToken row(UUID id, String secret, RefreshTokenStatus status, Instant expiresAt, UUID accessJti) {
        RefreshToken token = new RefreshToken(id, FAMILY_ID, USER_ID, sha256Hex(secret), null, accessJti, expiresAt);
        ReflectionTestUtils.setField(token, "status", status);
        ReflectionTestUtils.setField(token, "createdAt", Instant.now());
        return token;
    }

    @Test
    void issuePersistsAnActiveRowWithTheHashedSecretAndReturnsRowDotSecret() {
        UUID accessJti = UUID.randomUUID();

        String raw = service.issue(user(), accessJti.toString());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        String[] parts = raw.split("\\.", 2);
        assertThat(parts).hasSize(2);
        assertThat(parts[0]).isEqualTo(saved.getId().toString());
        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(parts[1]));
        assertThat(saved.getTokenHash()).isNotEqualTo(parts[1]);
        assertThat(saved.getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
        assertThat(saved.getParentId()).isNull();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getIssuedAccessJti()).isEqualTo(accessJti);
        assertThat(saved.getFamilyId()).isNotNull();
    }

    @Test
    void rotateHappyPathRotatesTheParentAndPersistsAChildInTheSameFamily() {
        UUID rowId = UUID.randomUUID();
        String secret = "the-secret";
        RefreshToken parent = row(rowId, secret, RefreshTokenStatus.ACTIVE, future(), UUID.randomUUID());
        UUID newJti = UUID.randomUUID();

        when(repository.findById(rowId)).thenReturn(Optional.of(parent));
        User authUser = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(authUser));
        when(jwtService.generateToken(any(User.class))).thenReturn("newAccess");
        when(jwtService.extractJti("newAccess")).thenReturn(newJti.toString());
        when(repository.claimForRotation(eq(rowId), any(), any())).thenReturn(1);

        RefreshTokenService.RotationResult result = service.rotate(rowId + "." + secret);

        assertThat(result.accessToken()).isEqualTo("newAccess");
        verify(repository).claimForRotation(eq(rowId), any(), any());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken child = captor.getValue();
        assertThat(child.getFamilyId()).isEqualTo(FAMILY_ID);
        assertThat(child.getParentId()).isEqualTo(rowId);
        assertThat(child.getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
        assertThat(child.getIssuedAccessJti()).isEqualTo(newJti);
        assertThat(result.rawRefreshToken()).startsWith(child.getId().toString() + ".");
        verify(reuseResponder, never()).respondToReuse(any(), any());
    }

    @Test
    void rotateRejectsAWrongSecret() {
        UUID rowId = UUID.randomUUID();
        RefreshToken parent = row(rowId, "real-secret", RefreshTokenStatus.ACTIVE, future(), null);
        when(repository.findById(rowId)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.rotate(rowId + ".wrong-secret"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_REFRESH_TOKEN"));
        verify(repository, never()).claimForRotation(any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void rotateRejectsAnUnknownToken() {
        UUID rowId = UUID.randomUUID();
        when(repository.findById(rowId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate(rowId + ".whatever"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rotateRejectsAnExpiredToken() {
        UUID rowId = UUID.randomUUID();
        String secret = "s";
        RefreshToken parent = row(rowId, secret, RefreshTokenStatus.ACTIVE,
                Instant.now().minus(1, ChronoUnit.MINUTES), null);
        when(repository.findById(rowId)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.rotate(rowId + "." + secret))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).claimForRotation(any(), any(), any());
    }

    @Test
    void rotateRejectsAnAlreadyRevokedTokenWithoutRespondingToReuse() {
        UUID rowId = UUID.randomUUID();
        String secret = "s";
        RefreshToken parent = row(rowId, secret, RefreshTokenStatus.REVOKED, future(), null);
        when(repository.findById(rowId)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.rotate(rowId + "." + secret))
                .isInstanceOf(BusinessException.class);
        verify(reuseResponder, never()).respondToReuse(any(), any());
    }

    @Test
    void rotateDetectsReuseOnAReplayedRotatedTokenAndDelegatesTheFamilyResponse() {
        UUID rowId = UUID.randomUUID();
        String secret = "s";
        RefreshToken parent = row(rowId, secret, RefreshTokenStatus.ROTATED, future(), UUID.randomUUID());
        when(repository.findById(rowId)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.rotate(rowId + "." + secret))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_REFRESH_TOKEN"));

        // The theft response is delegated to the responder (separate REQUIRES_NEW tx).
        verify(reuseResponder).respondToReuse(eq(FAMILY_ID), eq(USER_ID));
    }

    @Test
    void benignDoubleSubmitWithinGraceReturnsTheSameTokensWithoutRespondingToReuse() {
        UUID rowId = UUID.randomUUID();
        String secret = "s";
        RefreshToken parent = row(rowId, secret, RefreshTokenStatus.ACTIVE, future(), UUID.randomUUID());

        when(repository.findById(rowId)).thenReturn(Optional.of(parent));
        User authUser = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(authUser));
        when(jwtService.generateToken(any(User.class))).thenReturn("newAccess");
        when(jwtService.extractJti("newAccess")).thenReturn(UUID.randomUUID().toString());
        // First call wins the rotation race (1); the concurrent replay loses it (0).
        when(repository.claimForRotation(eq(rowId), any(), any())).thenReturn(1, 0);

        RefreshTokenService.RotationResult first = service.rotate(rowId + "." + secret);
        RefreshTokenService.RotationResult second = service.rotate(rowId + "." + secret);

        assertThat(second.accessToken()).isEqualTo(first.accessToken());
        assertThat(second.rawRefreshToken()).isEqualTo(first.rawRefreshToken());
        verify(reuseResponder, never()).respondToReuse(any(), any()); // never a false-positive logout
    }

    @Test
    void revokeFamilyOfDelegatesToTheResponderForAKnownToken() {
        UUID rowId = UUID.randomUUID();
        RefreshToken row = row(rowId, "s", RefreshTokenStatus.ACTIVE, future(), UUID.randomUUID());
        when(repository.findById(rowId)).thenReturn(Optional.of(row));

        service.revokeFamilyOf(rowId + ".s");

        verify(reuseResponder).revokeFamily(FAMILY_ID);
    }

    @Test
    void revokeFamilyOfNeverThrowsAndDoesNothingForAGarbageOrUnknownToken() {
        service.revokeFamilyOf("not-a-token");                          // unparseable → no-op

        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());
        service.revokeFamilyOf(unknown + ".secret");                    // unknown → no-op

        verify(reuseResponder, never()).revokeFamily(any());
    }

    private static Instant future() {
        return Instant.now().plus(7, ChronoUnit.DAYS);
    }
}
