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

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

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
