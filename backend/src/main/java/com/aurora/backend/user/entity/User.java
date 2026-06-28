package com.aurora.backend.user.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.user.role.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    // Orthogonal to `enabled`: a soft per-action flag (gates order placement), NOT an auth
    // boundary. New accounts start false; pre-feature accounts were grandfathered true (V12).
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    // Optional, opt-in TOTP second factor (OWASP A07). `mfaSecret` is the AES-GCM ciphertext of the
    // Base32 TOTP secret (never plaintext); it is set while an enrollment is pending with
    // `mfaEnabled` still false, and flipped to true only once a valid code confirms the enrollment.
    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "mfa_secret", length = 255)
    private String mfaSecret;

    @Column(name = "mfa_enrolled_at")
    private Instant mfaEnrolledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            Role role,
            boolean enabled
    ) {
        this(email, passwordHash, firstName, lastName, null, role, enabled);
    }

    public User(
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            String phone,
            Role role,
            boolean enabled
    ) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.role = role;
        this.enabled = enabled;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPhone() {
        return phone;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    /** True while a temporary login lock is still in effect. */
    public boolean isLoginLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Records a failed login. Once {@code maxAttempts} consecutive failures are
     * reached, locks the account for {@code lockDuration}. Returns {@code true}
     * only when this failure triggered a fresh lock, so the caller can audit it.
     */
    public boolean recordFailedLogin(int maxAttempts, Duration lockDuration, Instant now) {
        failedLoginAttempts += 1;
        if (failedLoginAttempts >= maxAttempts && !isLoginLocked(now)) {
            lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    /** Clears failure tracking after a successful authentication. */
    public void resetFailedLogins() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    /**
     * The only sanctioned write path for the BCrypt hash besides the constructors
     * (used by password reset). Callers MUST pass an already-encoded value, never a
     * raw password. {@code @PreUpdate} bumps {@code updatedAt}.
     */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    /** Marks the account's email as verified (sole write path; {@code @PreUpdate} bumps updatedAt). */
    public void verifyEmail() {
        this.emailVerified = true;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public String getMfaSecret() {
        return mfaSecret;
    }

    public Instant getMfaEnrolledAt() {
        return mfaEnrolledAt;
    }

    /**
     * Stores a freshly-minted, AES-GCM-encrypted TOTP secret as a PENDING enrollment without
     * enabling MFA. Re-enrolling simply overwrites the pending secret (idempotent). Enabling
     * is a separate, code-confirmed step ({@link #enableMfa(Instant)}).
     */
    public void beginMfaEnrollment(String encryptedSecret) {
        this.mfaSecret = encryptedSecret;
        this.mfaEnabled = false;
        this.mfaEnrolledAt = null;
    }

    /**
     * Activates MFA once a valid TOTP code has confirmed a pending enrollment. Requires a stored
     * secret — a guard against enabling MFA with no second factor on file.
     */
    public void enableMfa(Instant now) {
        if (mfaSecret == null) {
            throw new IllegalStateException("Cannot enable MFA without a pending secret.");
        }
        this.mfaEnabled = true;
        this.mfaEnrolledAt = now;
    }

    /** Turns MFA off and clears all enrollment state (sole disable write path). */
    public void disableMfa() {
        this.mfaEnabled = false;
        this.mfaSecret = null;
        this.mfaEnrolledAt = null;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
