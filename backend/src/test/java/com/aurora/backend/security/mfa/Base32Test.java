package com.aurora.backend.security.mfa;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the Base32 codec against the RFC 4648 §10 test vectors (unpadded, TOTP convention). */
class Base32Test {

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void encodesTheRfc4648Vectors() {
        assertThat(Base32.encode(ascii(""))).isEmpty();
        assertThat(Base32.encode(ascii("f"))).isEqualTo("MY");
        assertThat(Base32.encode(ascii("fo"))).isEqualTo("MZXQ");
        assertThat(Base32.encode(ascii("foo"))).isEqualTo("MZXW6");
        assertThat(Base32.encode(ascii("foob"))).isEqualTo("MZXW6YQ");
        assertThat(Base32.encode(ascii("fooba"))).isEqualTo("MZXW6YTB");
        assertThat(Base32.encode(ascii("foobar"))).isEqualTo("MZXW6YTBOI");
    }

    @Test
    void decodesUnpaddedPaddedLowercaseAndSpaced() {
        assertThat(new String(Base32.decode("MZXW6YTBOI"), StandardCharsets.US_ASCII)).isEqualTo("foobar");
        assertThat(new String(Base32.decode("MZXW6YTBOI======"), StandardCharsets.US_ASCII)).isEqualTo("foobar");
        assertThat(new String(Base32.decode("mzxw6ytboi"), StandardCharsets.US_ASCII)).isEqualTo("foobar");
        assertThat(new String(Base32.decode("MZXW 6YTB OI"), StandardCharsets.US_ASCII)).isEqualTo("foobar");
    }

    @Test
    void roundTripsArbitraryBytes() {
        byte[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, (byte) 0xFF, (byte) 0x80, 127, -1};
        assertThat(Base32.decode(Base32.encode(data))).isEqualTo(data);
    }

    @Test
    void rejectsACharacterOutsideTheAlphabet() {
        assertThatThrownBy(() -> Base32.decode("MZXW0"))   // '0' and '1' are not in RFC 4648 Base32
                .isInstanceOf(IllegalArgumentException.class);
    }
}
