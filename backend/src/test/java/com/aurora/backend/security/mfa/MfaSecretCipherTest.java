package com.aurora.backend.security.mfa;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AES-GCM round-trip, IV-uniqueness, and tamper-detection for the at-rest TOTP secret cipher. */
class MfaSecretCipherTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXP";   // a Base32 TOTP secret
    private final MfaSecretCipher cipher = new MfaSecretCipher(new byte[32]);   // 256-bit test key

    @Test
    void roundTripsTheSecret() {
        assertThat(cipher.decrypt(cipher.encrypt(SECRET))).isEqualTo(SECRET);
    }

    @Test
    void producesDistinctCiphertextEachTimeFromTheRandomIv() {
        assertThat(cipher.encrypt(SECRET)).isNotEqualTo(cipher.encrypt(SECRET));
    }

    @Test
    void rejectsATamperedCiphertext() {
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt(SECRET));
        raw[raw.length - 1] ^= 0x01;   // flip a bit in the GCM tag
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAWrongSizedKey() {
        assertThatThrownBy(() -> new MfaSecretCipher(new byte[20]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
