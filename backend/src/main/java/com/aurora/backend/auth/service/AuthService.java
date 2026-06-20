package com.aurora.backend.auth.service;

import java.time.Instant;
import java.util.Locale;

import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.auth.dto.AuthResponse;
import com.aurora.backend.auth.dto.AuthUserResponse;
import com.aurora.backend.auth.dto.LoginRequest;
import com.aurora.backend.auth.dto.RegisterRequest;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.security.jwt.JwtService;
import com.aurora.backend.security.token.TokenDenylistService;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;
import com.aurora.backend.user.role.Role;
import io.jsonwebtoken.JwtException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final TokenDenylistService tokenDenylistService;
    private final AuditLogService auditLogService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            LoginAttemptService loginAttemptService,
            TokenDenylistService tokenDenylistService,
            AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.tokenDenylistService = tokenDenylistService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_EXISTS",
                    "Email is already registered."
            );
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.firstName().trim(),
                request.lastName().trim(),
                normalizePhone(request.phone()),
                Role.CUSTOMER,
                true
        );

        User savedUser = userRepository.save(user);
        log.info("Registration succeeded (email={}).", email);
        return buildAuthResponse(savedUser);
    }

    // Not @Transactional on purpose: failed-attempt accounting must commit even
    // though a failed login throws. LoginAttemptService owns those transactions.
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        Instant now = Instant.now();

        // Reject a locked account before authenticating, so the lock can't be
        // worn down by continued guessing. Generic message → no user enumeration.
        if (loginAttemptService.isLocked(email, now)) {
            log.warn("Login blocked: account temporarily locked (email={}).", email);
            throw invalidCredentials();
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password())
            );
        } catch (AuthenticationException exception) {
            loginAttemptService.recordFailure(email, now);
            log.warn("Login failed (email={}).", email);
            throw invalidCredentials();
        }

        loginAttemptService.recordSuccess(email);

        User user = userRepository.findByEmail(email).orElseThrow(this::invalidCredentials);
        log.info("Login succeeded (email={}).", email);
        return buildAuthResponse(user);
    }

    /**
     * Server-side logout: revokes the caller's current access token so it is
     * rejected for the remainder of its lifetime (OWASP A07). Best-effort —
     * a token that can't be parsed leaves nothing to revoke.
     */
    public void logout(String authorizationHeader, User actor) {
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            String token = authorizationHeader.substring(BEARER_PREFIX.length());
            try {
                tokenDenylistService.revoke(jwtService.extractJti(token), jwtService.extractExpiration(token));
            } catch (JwtException | IllegalArgumentException exception) {
                log.debug("Logout: token could not be parsed for revocation ({}).",
                        exception.getClass().getSimpleName());
            }
        }
        auditLogService.log(AuditEventType.LOGOUT, actor, "User", actor.getId(),
                "User logged out; access token revoked.");
        log.info("Logout: access token revoked (email={}).", actor.getEmail());
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateToken(user);
        return AuthResponse.bearer(
                accessToken,
                jwtService.getExpirationMinutes(),
                AuthUserResponse.from(user)
        );
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid email or password."
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Optional field: trim, and store a blank phone as NULL (the @Pattern allows an empty string). */
    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.trim();
    }
}
