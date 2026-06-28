package com.aurora.backend.security.token;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.security.jwt.JwtService;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RefreshTokenReuseResponder}: the family-wide theft + logout
 * response (revoke family + denylist its access tokens + audit on reuse). The
 * headline guard is that {@code respondToReuse} runs in its OWN transaction
 * ({@code REQUIRES_NEW}) — that is what stops the 401 thrown by the caller from
 * rolling back the theft response (the CRITICAL finding from the security review).
 */
class RefreshTokenReuseResponderTest {

    private static final UUID FAMILY_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final TokenDenylistService tokenDenylistService = mock(TokenDenylistService.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final RefreshTokenReuseResponder responder = new RefreshTokenReuseResponder(
            repository, tokenDenylistService, jwtService, userRepository, auditLogService);

    private RefreshToken rowWithAccessJti(UUID accessJti) {
        RefreshToken row = new RefreshToken(
                UUID.randomUUID(), FAMILY_ID, USER_ID, "hash", null, accessJti,
                Instant.now().plus(7, ChronoUnit.DAYS));
        ReflectionTestUtils.setField(row, "createdAt", Instant.now());
        return row;
    }

    @Test
    void respondToReuseRevokesTheFamilyDenylistsAccessTokensAndAudits() {
        UUID accessJti = UUID.randomUUID();
        when(jwtService.getExpirationMinutes()).thenReturn(15L);
        when(repository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(rowWithAccessJti(accessJti)));
        User actor = mock(User.class);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(actor));

        responder.respondToReuse(FAMILY_ID, USER_ID);

        verify(repository).revokeFamily(eq(FAMILY_ID), any());
        verify(tokenDenylistService).revoke(eq(accessJti.toString()), any());
        verify(auditLogService).log(eq(AuditEventType.REFRESH_TOKEN_REUSED), eq(actor), any(), eq(FAMILY_ID), any());
    }

    @Test
    void revokeFamilyRevokesAndDenylistsButDoesNotAudit() {
        UUID accessJti = UUID.randomUUID();
        when(jwtService.getExpirationMinutes()).thenReturn(15L);
        when(repository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(rowWithAccessJti(accessJti)));

        responder.revokeFamily(FAMILY_ID);

        verify(repository).revokeFamily(eq(FAMILY_ID), any());
        verify(tokenDenylistService).revoke(eq(accessJti.toString()), any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void respondToReuseRunsInItsOwnTransactionSoTheCallers401CannotRollItBack() throws Exception {
        Transactional tx = RefreshTokenReuseResponder.class
                .getMethod("respondToReuse", UUID.class, UUID.class)
                .getAnnotation(Transactional.class);

        assertThat(tx).as("respondToReuse must be transactional").isNotNull();
        assertThat(tx.propagation())
                .as("the theft response must commit independently of the rolling-back caller")
                .isEqualTo(Propagation.REQUIRES_NEW);
    }
}
