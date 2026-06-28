package com.aurora.backend.auth.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.auth.dto.MfaEnrollResponse;
import com.aurora.backend.auth.dto.MfaStatusResponse;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.security.mfa.Base32;
import com.aurora.backend.security.mfa.MfaProperties;
import com.aurora.backend.security.mfa.MfaSecretCipher;
import com.aurora.backend.security.mfa.TotpGenerator;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opt-in TOTP MFA enrollment (OWASP A07), the enrollment-only increment — the login flow stays
 * unchanged and is gated in a later increment (see {@code docs/appsec/mfa-design.md}).
 *
 * <p>The flow is two-step: {@link #enroll} mints a 160-bit secret, stores it ENCRYPTED as a pending
 * enrollment ({@code mfaEnabled} still false), and returns the Base32 secret + an {@code otpauth://}
 * URI so the user can add it to an authenticator app; {@link #confirm} only flips MFA on once a
 * valid TOTP proves the user holds the same secret. Each method loads the {@link User} fresh inside
 * its transaction, mutates via the entity's intention-revealing methods, and relies on
 * dirty-checking to persist. The raw secret/code are never logged.
 */
@Service
public class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);

    /** 160-bit secret (RFC 6238 §4 / RFC 4226 recommends >= 128-bit; 160 matches HMAC-SHA1's block). */
    private static final int SECRET_BYTES = 20;
    /** ±1 30-second step absorbs clock skew between the server and the authenticator app. */
    private static final int VERIFY_WINDOW = 1;

    private final UserRepository userRepository;
    private final MfaSecretCipher cipher;
    private final AuditLogService auditLogService;
    private final String issuer;
    private final SecureRandom random = new SecureRandom();

    public MfaService(
            UserRepository userRepository,
            MfaSecretCipher cipher,
            AuditLogService auditLogService,
            MfaProperties properties
    ) {
        this.userRepository = userRepository;
        this.cipher = cipher;
        this.auditLogService = auditLogService;
        this.issuer = properties.issuer();
    }

    /**
     * Begins (or restarts) enrollment: generates a fresh secret, stores it encrypted as pending,
     * and returns it once for the user to register. Idempotent — a re-enroll overwrites any prior
     * pending secret. Does NOT enable MFA. Rejected if MFA is already enabled (disable first).
     */
    @Transactional
    public MfaEnrollResponse enroll(User authenticatedUser) {
        User user = loadFresh(authenticatedUser);
        if (user.isMfaEnabled()) {
            throw new BusinessException(HttpStatus.CONFLICT, "MFA_ALREADY_ENABLED",
                    "MFA is already enabled. Disable it before enrolling again.");
        }

        byte[] secretBytes = new byte[SECRET_BYTES];
        random.nextBytes(secretBytes);
        String base32Secret = Base32.encode(secretBytes);

        user.beginMfaEnrollment(cipher.encrypt(base32Secret));

        log.info("MFA enrollment started (userId={}).", user.getId());
        return new MfaEnrollResponse(base32Secret, buildOtpauthUri(user.getEmail(), base32Secret));
    }

    /**
     * Confirms a pending enrollment by verifying a current TOTP code against the stored secret; on
     * success enables MFA. Any failure (no pending secret / wrong code) collapses to a generic
     * {@code 400 MFA_INVALID_CODE} so it gives nothing away.
     */
    @Transactional
    public void confirm(User authenticatedUser, String code) {
        User user = loadFresh(authenticatedUser);
        if (user.isMfaEnabled()) {
            throw new BusinessException(HttpStatus.CONFLICT, "MFA_ALREADY_ENABLED",
                    "MFA is already enabled.");
        }
        if (user.getMfaSecret() == null) {
            throw invalidCode();
        }
        if (!verify(user.getMfaSecret(), code)) {
            throw invalidCode();
        }

        user.enableMfa(Instant.now());
        auditLogService.log(AuditEventType.MFA_ENABLED, user, "USER", user.getId(), "MFA enabled.");
        log.info("MFA enabled (userId={}).", user.getId());
    }

    /**
     * Disables MFA after verifying a current TOTP code against the stored secret, then clears all
     * enrollment state. Requires MFA to be currently enabled. A wrong code is rejected.
     */
    @Transactional
    public void disable(User authenticatedUser, String code) {
        User user = loadFresh(authenticatedUser);
        if (!user.isMfaEnabled() || user.getMfaSecret() == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "MFA_NOT_ENABLED", "MFA is not enabled.");
        }
        if (!verify(user.getMfaSecret(), code)) {
            throw invalidCode();
        }

        user.disableMfa();
        auditLogService.log(AuditEventType.MFA_DISABLED, user, "USER", user.getId(), "MFA disabled.");
        log.info("MFA disabled (userId={}).", user.getId());
    }

    @Transactional(readOnly = true)
    public MfaStatusResponse status(User authenticatedUser) {
        return new MfaStatusResponse(loadFresh(authenticatedUser).isMfaEnabled());
    }

    /** Decrypts the stored Base32 secret and verifies the code in constant time (±1 step window). */
    private boolean verify(String encryptedSecret, String code) {
        byte[] secretBytes = Base32.decode(cipher.decrypt(encryptedSecret));
        return TotpGenerator.verify(secretBytes, code, Instant.now(), VERIFY_WINDOW);
    }

    private String buildOtpauthUri(String email, String base32Secret) {
        String encodedIssuer = urlEncode(issuer);
        String label = encodedIssuer + ":" + urlEncode(email);
        return "otpauth://totp/" + label
                + "?secret=" + base32Secret
                + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private static String urlEncode(String value) {
        // otpauth:// is a URI, not a form body: a space must be %20, not the '+' that
        // application/x-www-form-urlencoded (URLEncoder's default) would emit.
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Re-reads the user inside the transaction so mutations land on a managed entity. */
    private User loadFresh(User authenticatedUser) {
        UUID id = authenticatedUser.getId();
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Authenticated user was not found."));
    }

    private BusinessException invalidCode() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "MFA_INVALID_CODE",
                "The verification code is invalid.");
    }
}
