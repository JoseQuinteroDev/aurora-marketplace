package com.aurora.backend.security.jwt;

import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Fails fast (or warns loudly) when the JWT signing secret is a known committed
 * placeholder. The {@code @Size(min = 32)} validation on {@link JwtProperties}
 * does NOT catch this: the development defaults shipped in {@code application.yml}
 * and {@code docker-compose.yml} are long enough to pass it, so an environment
 * that forgets to set {@code APP_SECURITY_JWT_SECRET} would otherwise run on a
 * publicly-known HMAC key — anyone with the repo could then forge an admin token
 * (OWASP A02 / A05).
 *
 * <p>Policy: under the {@code prod} profile a placeholder secret is fatal (the
 * app refuses to start); in every other environment it is a loud warning so
 * local development and tests keep working out of the box.
 */
@Component
public class JwtSecretValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretValidator.class);

    /** Secrets committed to the repository for local development — never valid in production. */
    private static final Set<String> KNOWN_INSECURE_SECRETS = Set.of(
            "aurora-marketplace-development-secret-change-me-before-production-1234567890",
            "dev-only-insecure-jwt-secret-change-me-32"
    );

    private final JwtProperties properties;
    private final Environment environment;

    public JwtSecretValidator(JwtProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void validateSigningSecret() {
        String secret = properties.secret();
        boolean insecure = secret == null || secret.isBlank() || KNOWN_INSECURE_SECRETS.contains(secret);
        if (!insecure) {
            return;
        }

        String problem = "APP_SECURITY_JWT_SECRET is unset or set to a known development placeholder. "
                + "A publicly-known signing key lets anyone forge valid JWTs (including admin tokens).";

        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    problem + " Refusing to start under the 'prod' profile — provide a strong, unique secret.");
        }

        log.warn("SECURITY: {} This is tolerated outside production; set a strong, unique secret "
                + "(>= 32 chars, from a secret manager) before deploying.", problem);
    }
}
