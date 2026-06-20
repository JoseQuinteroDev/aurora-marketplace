package com.aurora.backend.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtSecretValidator} (OWASP A02/A05): a known committed
 * placeholder secret is fatal under {@code prod} but only a warning elsewhere.
 */
class JwtSecretValidatorTest {

    private static final String PLACEHOLDER =
            "aurora-marketplace-development-secret-change-me-before-production-1234567890";
    private static final String STRONG =
            "a-strong-unique-production-signing-secret-of-good-length-9f3c1d";

    @Test
    void failsFastUnderProdWithAPlaceholderSecret() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        JwtSecretValidator validator = new JwtSecretValidator(new JwtProperties(PLACEHOLDER, 60), env);

        assertThatThrownBy(validator::validateSigningSecret)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsFastUnderProdWithABlankSecret() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        JwtSecretValidator validator = new JwtSecretValidator(new JwtProperties("   ", 60), env);

        assertThatThrownBy(validator::validateSigningSecret)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void toleratesThePlaceholderOutsideProd() {
        MockEnvironment env = new MockEnvironment(); // no active profile
        JwtSecretValidator validator = new JwtSecretValidator(new JwtProperties(PLACEHOLDER, 60), env);

        assertThatCode(validator::validateSigningSecret).doesNotThrowAnyException();
    }

    @Test
    void acceptsAStrongSecretUnderProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        JwtSecretValidator validator = new JwtSecretValidator(new JwtProperties(STRONG, 60), env);

        assertThatCode(validator::validateSigningSecret).doesNotThrowAnyException();
    }
}
