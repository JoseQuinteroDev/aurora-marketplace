package com.aurora.backend.security.mfa;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the TOTP MFA second factor (OWASP A07).
 *
 * <p>{@code encryptionKey} is a base64-encoded AES key (16/24/32 bytes after decoding) used by
 * {@link MfaSecretCipher} to encrypt the Base32 TOTP secret at rest. A placeholder/invalid key is
 * tolerated outside production but fatal under the {@code prod} profile — see {@link MfaKeyValidator},
 * which mirrors {@code JwtSecretValidator}. {@code issuer} is the label shown in authenticator apps
 * and embedded in the {@code otpauth://} provisioning URI.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.mfa")
public record MfaProperties(
        @NotBlank(message = "MFA encryption key is required.")
        String encryptionKey,

        @NotBlank(message = "MFA issuer is required.")
        String issuer
) {
}
