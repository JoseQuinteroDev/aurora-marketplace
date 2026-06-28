package com.aurora.backend.security.mfa;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 30 s step, 6 digits) — the algorithm authenticator apps
 * (Google Authenticator, Authy, 1Password…) implement. Pure + dependency-free so it is
 * verifiable against the RFC 6238 published test vectors. {@link #verify} accepts a ±step
 * skew window and uses a constant-time, non-short-circuiting comparison so it leaks neither
 * the code nor which step matched.
 */
public final class TotpGenerator {

    public static final int DEFAULT_DIGITS = 6;
    public static final int STEP_SECONDS = 30;
    private static final String HMAC = "HmacSHA1";
    private static final int[] POW10 = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};

    private TotpGenerator() {
    }

    /** The 6-digit code for the given Unix instant (default step). */
    public static String generate(byte[] secret, Instant time) {
        return generate(secret, time.getEpochSecond() / STEP_SECONDS, DEFAULT_DIGITS);
    }

    /** The {@code digits}-length code for an explicit step counter (the RFC's T). */
    public static String generate(byte[] secret, long stepCounter, int digits) {
        byte[] hash = hmacSha1(secret, ByteBuffer.allocate(Long.BYTES).putLong(stepCounter).array());
        int offset = hash[hash.length - 1] & 0x0F;            // dynamic truncation (RFC 4226 §5.3)
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        return padLeft(binary % POW10[digits], digits);
    }

    /**
     * Verifies a user-supplied code against the secret around {@code time}, tolerating ±{@code
     * window} steps of clock skew. Compares every candidate (no early exit) in constant time.
     */
    public static boolean verify(byte[] secret, String code, Instant time, int window) {
        if (code == null) {
            return false;
        }
        String trimmed = code.trim();
        long step = time.getEpochSecond() / STEP_SECONDS;
        boolean match = false;
        for (long i = -window; i <= window; i++) {
            match |= constantTimeEquals(generate(secret, step + i, DEFAULT_DIGITS), trimmed);
        }
        return match;
    }

    private static byte[] hmacSha1(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(message);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA1 unavailable", impossible);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    private static String padLeft(int value, int digits) {
        StringBuilder s = new StringBuilder(Integer.toString(value));
        while (s.length() < digits) {
            s.insert(0, '0');
        }
        return s.toString();
    }
}
