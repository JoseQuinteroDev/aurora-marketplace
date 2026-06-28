package com.aurora.backend.security.mfa;

import java.util.Base64;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Fails fast (or warns loudly) when the MFA secret-encryption key is missing, a known committed
 * placeholder, or not a valid AES key length. Mirrors {@code JwtSecretValidator}: the
 * {@code @NotBlank} bind validation on {@link MfaProperties} does NOT catch this — the development
 * default shipped in {@code application.yml} is non-blank and would otherwise let a deployment that
 * forgets {@code APP_SECURITY_MFA_ENCRYPTION_KEY} encrypt every user's TOTP secret under a
 * publicly-known key (OWASP A02 / A05), making the at-rest encryption worthless.
 *
 * <p>Policy: under the {@code prod} profile a placeholder/blank/wrong-length key is fatal (the app
 * refuses to start); in every other environment it is a loud warning so local development and tests
 * keep working out of the box.
 */
@Component
public class MfaKeyValidator {

    private static final Logger log = LoggerFactory.getLogger(MfaKeyValidator.class);

    /** Keys committed to the repository for local development — never valid in production. */
    private static final Set<String> KNOWN_INSECURE_KEYS = Set.of(
            "ZGV2LW9ubHktaW5zZWN1cmUtbWZhLWtleS0zMmJ5dGU="
    );

    private final MfaProperties properties;
    private final Environment environment;

    public MfaKeyValidator(MfaProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void validateEncryptionKey() {
        String key = properties.encryptionKey();
        String problem = describeProblem(key);
        if (problem == null) {
            return;
        }

        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    problem + " Refusing to start under the 'prod' profile — provide a strong, unique "
                            + "base64-encoded AES key (16/24/32 bytes).");
        }

        log.warn("SECURITY: {} This is tolerated outside production; set a strong, unique "
                + "base64 AES key (from a secret manager) before deploying.", problem);
    }

    /** Returns a human-readable problem description, or {@code null} when the key is acceptable. */
    private String describeProblem(String key) {
        if (key == null || key.isBlank()) {
            return "APP_SECURITY_MFA_ENCRYPTION_KEY is unset or blank; MFA secrets cannot be encrypted.";
        }
        if (KNOWN_INSECURE_KEYS.contains(key)) {
            return "APP_SECURITY_MFA_ENCRYPTION_KEY is set to a known development placeholder. "
                    + "A publicly-known key makes the at-rest encryption of TOTP secrets worthless.";
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException notBase64) {
            return "APP_SECURITY_MFA_ENCRYPTION_KEY is not valid base64.";
        }
        if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
            return "APP_SECURITY_MFA_ENCRYPTION_KEY must decode to a 16/24/32-byte AES key (got "
                    + decoded.length + " bytes).";
        }
        return null;
    }
}
