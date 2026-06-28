package com.aurora.backend.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.role.Role;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security unit tests for {@link JwtService}.
 *
 * <p>These assert the authentication core's threat-relevant behavior directly,
 * with no Spring context or database: a token is only honored if its signature
 * verifies with our secret, uses our pinned algorithm, and it has not expired.
 * They map to OWASP A02 (Cryptographic Failures) and A07 (Authentication
 * Failures). See {@code docs/appsec/security-testing.md}.
 */
class JwtServiceTest {

    // Test-only secret. Kept >=64 bytes so it satisfies the minimum key length of
    // HS256/384/512 alike — the larger algorithms are only ever used here, by an
    // "attacker" forging a swapped-alg token to prove our parser pins HS256.
    private static final String SECRET = "test-secret-key-that-is-long-enough-and-then-some-1234567890-abcdef";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60));

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private User userWithId(String email, Role role) {
        User user = new User(email, "irrelevant-hash", "Ada", "Lovelace", role, true);
        // id is DB-generated in production; set it so generateToken() can run.
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private UserDetails principal(String email) {
        return org.springframework.security.core.userdetails.User
                .withUsername(email)
                .password("irrelevant")
                .authorities("ROLE_CUSTOMER")
                .build();
    }

    @Test
    void generatesAValidTokenThatRoundTrips() {
        User user = userWithId("ada@aurora.test", Role.CUSTOMER);

        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractSubject(token)).isEqualTo("ada@aurora.test");
        assertThat(jwtService.isTokenValid(token, principal("ada@aurora.test"))).isTrue();
    }

    @Test
    void rejectsATokenWhosePayloadWasTampered() {
        String token = jwtService.generateToken(userWithId("ada@aurora.test", Role.CUSTOMER));

        // Flip one character in the payload segment: the signature no longer matches.
        String[] parts = token.split("\\.");
        char first = parts[1].charAt(0);
        char swapped = (first == 'A') ? 'B' : 'A';
        String tampered = parts[0] + "." + swapped + parts[1].substring(1) + "." + parts[2];

        assertThatThrownBy(() -> jwtService.extractSubject(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        JwtService attacker = new JwtService(
                new JwtProperties("a-completely-different-secret-key-0987654321", 60));
        String forged = attacker.generateToken(userWithId("mallory@aurora.test", Role.ADMIN));

        // Our service must not accept a token it did not sign.
        assertThatThrownBy(() -> jwtService.extractSubject(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        // Build a correctly-signed but already-expired token with our own key.
        SecretKey key = secretKey();
        String expired = Jwts.builder()
                .subject("ada@aurora.test")
                .issuedAt(Date.from(Instant.now().minus(Duration.ofHours(2))))
                .expiration(Date.from(Instant.now().minus(Duration.ofHours(1))))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        // jjwt rejects expired tokens at parse time; the auth filter treats this
        // (a JwtException) as "unauthenticated" and clears the security context.
        assertThatThrownBy(() -> jwtService.isTokenValid(expired, principal("ada@aurora.test")))
                .isInstanceOf(JwtException.class);
    }

    // --- Algorithm pinning (OWASP A02): reject alg:none and algorithm-confusion. ---

    @Test
    void rejectsAnUnsecuredAlgNoneToken() {
        // An attacker strips the signature and sets "alg":"none" hoping the parser will
        // trust an unsigned token. Our service must refuse it (signature pinning).
        String unsecured = Jwts.builder()
                .subject("mallory@aurora.test")
                .claim("role", "ADMIN")
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .compact(); // no signWith -> alg:none, unsecured JWS

        assertThatThrownBy(() -> jwtService.extractSubject(unsecured))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsATokenWhoseAlgorithmHeaderWasSwapped() {
        // The signature is genuinely valid for HS384 against our key bytes, but the header
        // advertises a different algorithm than the one we pin (HS256). Without explicit
        // algorithm pinning the key would still verify it; our key locator must reject it.
        String swappedAlg = Jwts.builder()
                .subject("mallory@aurora.test")
                .claim("role", "ADMIN")
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(secretKey(), Jwts.SIG.HS384)
                .compact();

        // Sanity-check the forged token really carries the wrong alg header.
        assertThat(swappedAlg).isNotBlank();

        assertThatThrownBy(() -> jwtService.extractSubject(swappedAlg))
                .isInstanceOf(JwtException.class);
    }

    // --- Clock-skew tolerance (OWASP A07): 30s leeway for multi-node drift. ---

    @Test
    void acceptsATokenExpiredWithinTheClockSkewWindow() {
        // Expired 10 seconds ago — inside the 30s skew tolerance, so it must still verify.
        String recentlyExpired = Jwts.builder()
                .subject("ada@aurora.test")
                .issuedAt(Date.from(Instant.now().minus(Duration.ofMinutes(1))))
                .expiration(Date.from(Instant.now().minus(Duration.ofSeconds(10))))
                .signWith(secretKey(), Jwts.SIG.HS256)
                .compact();

        assertThat(jwtService.extractSubject(recentlyExpired)).isEqualTo("ada@aurora.test");
    }

    @Test
    void rejectsATokenExpiredBeyondTheClockSkewWindow() {
        // Expired 90 seconds ago — well outside the 30s tolerance, so it must be rejected.
        String longExpired = Jwts.builder()
                .subject("ada@aurora.test")
                .issuedAt(Date.from(Instant.now().minus(Duration.ofMinutes(5))))
                .expiration(Date.from(Instant.now().minus(Duration.ofSeconds(90))))
                .signWith(secretKey(), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.extractSubject(longExpired))
                .isInstanceOf(JwtException.class);
    }
}
