package com.aurora.backend.security.mfa;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Authenticated symmetric encryption (AES-256-GCM) for the TOTP secret at rest. Unlike a
 * password, the secret must be RECOVERABLE to validate codes, so it is encrypted — not hashed —
 * under a server-held key (config: {@code app.security.mfa.encryption-key}, base64 16/24/32
 * bytes). A fresh random 12-byte IV is prepended to each ciphertext; GCM's auth tag makes
 * tampering detectable (decrypt throws). Output/input is base64 of {@code IV || ciphertext||tag}.
 */
public final class MfaSecretCipher {

    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public MfaSecretCipher(byte[] keyBytes) {
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("MFA encryption key must be 128/192/256-bit (got "
                    + (keyBytes.length * 8) + "-bit)");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("MFA secret encryption failed", failure);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Ciphertext too short");
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException tamperedOrWrongKey) {
            // GCM tag mismatch (tampering / wrong key) lands here — never returns garbage.
            throw new IllegalStateException("MFA secret decryption failed", tamperedOrWrongKey);
        }
    }
}
