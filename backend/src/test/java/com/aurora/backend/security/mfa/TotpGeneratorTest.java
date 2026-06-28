package com.aurora.backend.security.mfa;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the TOTP generator against the RFC 6238 Appendix B published vectors (SHA-1 seed),
 * truncated to the 6 digits authenticator apps display, plus the skew-window behaviour of verify.
 */
class TotpGeneratorTest {

    // RFC 6238 SHA-1 seed: ASCII "12345678901234567890" (20 bytes).
    private static final byte[] SEED = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesTheRfc6238SixDigitVectors() {
        assertThat(TotpGenerator.generate(SEED, Instant.ofEpochSecond(59L))).isEqualTo("287082");
        assertThat(TotpGenerator.generate(SEED, Instant.ofEpochSecond(1111111109L))).isEqualTo("081804");
        assertThat(TotpGenerator.generate(SEED, Instant.ofEpochSecond(1111111111L))).isEqualTo("050471");
        assertThat(TotpGenerator.generate(SEED, Instant.ofEpochSecond(1234567890L))).isEqualTo("005924");
        assertThat(TotpGenerator.generate(SEED, Instant.ofEpochSecond(2000000000L))).isEqualTo("279037");
    }

    @Test
    void verifyAcceptsTheCurrentCodeAndRejectsAWrongOrNullOne() {
        Instant t = Instant.ofEpochSecond(1111111109L);
        assertThat(TotpGenerator.verify(SEED, "081804", t, 1)).isTrue();
        assertThat(TotpGenerator.verify(SEED, " 081804 ", t, 1)).isTrue();   // trims
        assertThat(TotpGenerator.verify(SEED, "000000", t, 1)).isFalse();
        assertThat(TotpGenerator.verify(SEED, null, t, 1)).isFalse();
    }

    @Test
    void verifyToleratesExactlyOneStepOfClockSkew() {
        Instant t = Instant.ofEpochSecond(1111111109L);
        long step = t.getEpochSecond() / TotpGenerator.STEP_SECONDS;
        String previousStepCode = TotpGenerator.generate(SEED, step - 1, TotpGenerator.DEFAULT_DIGITS);

        assertThat(TotpGenerator.verify(SEED, previousStepCode, t, 1)).isTrue();   // within ±1
        assertThat(TotpGenerator.verify(SEED, previousStepCode, t, 0)).isFalse();  // not within ±0
    }
}
