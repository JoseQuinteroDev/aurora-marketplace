package com.aurora.backend.security.mfa;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * RFC 4648 Base32 (the alphabet authenticator apps expect for TOTP secrets). Encoding is
 * unpadded and uppercase (the TOTP convention); decoding is lenient — case-insensitive and
 * tolerant of '=' padding and whitespace, so a secret a user typed with spaces still decodes.
 * Pure + dependency-free so it's verifiable against the RFC vectors with no infrastructure.
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] DECODE = new int[128];

    static {
        Arrays.fill(DECODE, -1);
        for (int i = 0; i < ALPHABET.length(); i++) {
            char upper = ALPHABET.charAt(i);
            DECODE[upper] = i;
            DECODE[Character.toLowerCase(upper)] = i;
        }
    }

    private Base32() {
    }

    /** RFC 4648 Base32, no padding, uppercase. */
    public static String encode(byte[] data) {
        if (data.length == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                out.append(ALPHABET.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            out.append(ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return out.toString();
    }

    /** Decodes RFC 4648 Base32 (case-insensitive; ignores '=' and whitespace). */
    public static byte[] decode(String encoded) {
        String clean = encoded.replace("=", "").replaceAll("\\s", "");
        if (clean.isEmpty()) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(clean.length() * 5 / 8);
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            int value = c < 128 ? DECODE[c] : -1;
            if (value < 0) {
                throw new IllegalArgumentException("Illegal Base32 character: " + c);
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out.write((buffer >> bitsLeft) & 0xFF);
            }
        }
        return out.toByteArray();
    }
}
