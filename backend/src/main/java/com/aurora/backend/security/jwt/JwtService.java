package com.aurora.backend.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.aurora.backend.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    /**
     * The one signature algorithm we issue and will accept. Pinning this defends against
     * algorithm-confusion / {@code alg:none} downgrade attacks (OWASP A02): the verification
     * key is only handed to the parser when the JWS header advertises exactly this algorithm.
     */
    private static final String EXPECTED_ALG = Jwts.SIG.HS256.getId();

    /** Tolerated clock drift between nodes when validating exp/nbf (OWASP A07, multi-node skew). */
    private static final long CLOCK_SKEW_SECONDS = 30;

    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiration = now.plus(Duration.ofMinutes(properties.expirationMinutes()));

        return Jwts.builder()
                // Unique token id (jti) so a specific token can be revoked server-side.
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .claim("userId", user.getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                // Pin the algorithm explicitly so issuing and verifying stay symmetric.
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String extractSubject(String token) {
        return extractClaims(token).getSubject();
    }

    /** The token's unique id (jti) — used for server-side revocation. */
    public String extractJti(String token) {
        return extractClaims(token).getId();
    }

    /** The token's expiry instant — stored with a revocation so the denylist can self-prune. */
    public Instant extractExpiration(String token) {
        return extractClaims(token).getExpiration().toInstant();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String subject = extractSubject(token);
        return subject.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public long getExpirationMinutes() {
        return properties.expirationMinutes();
    }

    private boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                // Resolve the verification key via a locator that refuses any header whose
                // alg is not our pinned HS256 — so an alg-confusion or alg:none token is
                // rejected by intent, not merely by library default.
                .keyLocator(algorithmPinningKeyLocator())
                // Tolerate small inter-node clock drift on exp/nbf checks.
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * A {@link Locator} that returns the HMAC verification key only when the JWS header's
     * algorithm is exactly {@link #EXPECTED_ALG} (HS256); any other algorithm — including a
     * swapped/forged {@code alg} header — throws and the token is rejected. (An {@code alg:none}
     * token is an unsecured JWS, which {@code parseSignedClaims} rejects regardless.)
     */
    private Locator<Key> algorithmPinningKeyLocator() {
        SecretKey key = signingKey();
        return new io.jsonwebtoken.LocatorAdapter<>() {
            @Override
            protected Key locate(JwsHeader header) {
                String alg = header.getAlgorithm();
                if (!EXPECTED_ALG.equals(alg)) {
                    throw new SignatureException(
                            "Unexpected JWT signature algorithm '" + alg + "'; only "
                                    + EXPECTED_ALG + " is accepted");
                }
                return key;
            }
        };
    }

    private SecretKey signingKey() {
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(secretBytes);
    }
}
