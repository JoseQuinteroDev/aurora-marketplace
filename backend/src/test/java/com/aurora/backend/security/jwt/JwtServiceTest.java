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
 * verifies with our secret and it has not expired. They map to OWASP A02
 * (Cryptographic Failures) and A07 (Authentication Failures). See
 * {@code docs/appsec/security-testing.md}.
 */
class JwtServiceTest {

    // Test-only secret (>=32 chars, matching JwtProperties validation).
    private static final String SECRET = "test-secret-key-that-is-long-enough-1234567890";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60));

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
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .subject("ada@aurora.test")
                .issuedAt(Date.from(Instant.now().minus(Duration.ofHours(2))))
                .expiration(Date.from(Instant.now().minus(Duration.ofHours(1))))
                .signWith(key)
                .compact();

        // jjwt rejects expired tokens at parse time; the auth filter treats this
        // (a JwtException) as "unauthenticated" and clears the security context.
        assertThatThrownBy(() -> jwtService.isTokenValid(expired, principal("ada@aurora.test")))
                .isInstanceOf(JwtException.class);
    }
}
