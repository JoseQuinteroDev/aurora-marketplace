package com.aurora.backend.security.mfa;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MfaKeyValidator} (OWASP A02/A05): a known committed placeholder, a blank
 * key, non-base64, or a wrong-length key is fatal under {@code prod} but only a warning elsewhere;
 * a strong, correctly-sized base64 AES key is always accepted. Mirrors {@code JwtSecretValidatorTest}.
 */
class MfaKeyValidatorTest {

    // The dev placeholder shipped in application.yml ("dev-only-insecure-mfa-key-32byte", base64).
    private static final String PLACEHOLDER = "ZGV2LW9ubHktaW5zZWN1cmUtbWZhLWtleS0zMmJ5dGU=";
    // A real, strong 32-byte key, base64-encoded ("0123456789abcdef0123456789abcdef").
    private static final String STRONG = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String ISSUER = "Aurora Marketplace";

    @Test
    void failsFastUnderProdWithThePlaceholderKey() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        MfaKeyValidator validator = new MfaKeyValidator(new MfaProperties(PLACEHOLDER, ISSUER), env);

        assertThatThrownBy(validator::validateEncryptionKey)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsFastUnderProdWithABlankKey() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        MfaKeyValidator validator = new MfaKeyValidator(new MfaProperties("   ", ISSUER), env);

        assertThatThrownBy(validator::validateEncryptionKey)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsFastUnderProdWithAWrongLengthKey() {
        // base64 of "short" -> 5 bytes, not a valid AES key length.
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        MfaKeyValidator validator = new MfaKeyValidator(new MfaProperties("c2hvcnQ=", ISSUER), env);

        assertThatThrownBy(validator::validateEncryptionKey)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsFastUnderProdWithANonBase64Key() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        MfaKeyValidator validator = new MfaKeyValidator(new MfaProperties("not base64!!!", ISSUER), env);

        assertThatThrownBy(validator::validateEncryptionKey)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void toleratesThePlaceholderOutsideProd() {
        MockEnvironment env = new MockEnvironment(); // no active profile
        MfaKeyValidator validator = new MfaKeyValidator(new MfaProperties(PLACEHOLDER, ISSUER), env);

        assertThatCode(validator::validateEncryptionKey).doesNotThrowAnyException();
    }

    @Test
    void acceptsAStrongKeyUnderProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        MfaKeyValidator validator = new MfaKeyValidator(new MfaProperties(STRONG, ISSUER), env);

        assertThatCode(validator::validateEncryptionKey).doesNotThrowAnyException();
    }
}
