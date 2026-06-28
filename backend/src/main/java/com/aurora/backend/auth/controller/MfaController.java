package com.aurora.backend.auth.controller;

import com.aurora.backend.auth.dto.AuthResponse;
import com.aurora.backend.auth.dto.MfaCodeRequest;
import com.aurora.backend.auth.dto.MfaEnrollResponse;
import com.aurora.backend.auth.dto.MfaStatusResponse;
import com.aurora.backend.auth.dto.VerifyMfaRequest;
import com.aurora.backend.auth.service.AuthService;
import com.aurora.backend.auth.service.MfaService;
import com.aurora.backend.common.api.ApiResponse;
import com.aurora.backend.security.CurrentUserService;
import com.aurora.backend.user.entity.User;
import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MFA endpoints (OWASP A07). The four self-service endpoints — {@code enroll}, {@code confirm},
 * {@code disable}, {@code status} — require authentication: they are NOT in {@code SecurityConfig}'s
 * public permit-list, so the {@code anyRequest().authenticated()} rule applies and a user can only
 * manage their OWN second factor.
 *
 * <p>Thin adapter: the user is resolved from the security context via {@link CurrentUserService} and
 * never trusted from the request body; business rules live in {@link MfaService}.
 *
 * <p>The login-gating endpoint {@code POST /api/auth/mfa/verify} is the one exception — it is PUBLIC
 * (permitted in {@code SecurityConfig}, like {@code /refresh}): the caller has only just passed the
 * password step and holds no access token yet, only the opaque single-use {@code mfaToken}. It
 * delegates to {@link AuthService#verifyMfa}, the sole MFA-login token-issuing path.
 */
@RestController
@RequestMapping("/api/auth/mfa")
public class MfaController {

    private final MfaService mfaService;
    private final AuthService authService;
    private final CurrentUserService currentUserService;

    public MfaController(MfaService mfaService, AuthService authService,
            CurrentUserService currentUserService) {
        this.mfaService = mfaService;
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/verify")
    public ApiResponse<AuthResponse> verify(@Valid @RequestBody VerifyMfaRequest request) {
        // Public: completes a MFA_REQUIRED login by exchanging the single-use mfaToken + a current
        // TOTP code for the real access + refresh tokens. Any failure → generic 401.
        return ApiResponse.success("Login successful.", authService.verifyMfa(request));
    }

    @PostMapping("/enroll")
    public ApiResponse<MfaEnrollResponse> enroll(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        return ApiResponse.success("Scan the QR code, then confirm with a code.", mfaService.enroll(user));
    }

    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(
            @Valid @RequestBody MfaCodeRequest request,
            Authentication authentication
    ) {
        User user = currentUserService.getCurrentUser(authentication);
        mfaService.confirm(user, request.code());
        return ApiResponse.success("MFA enabled.");
    }

    @PostMapping("/disable")
    public ApiResponse<Void> disable(
            @Valid @RequestBody MfaCodeRequest request,
            Authentication authentication
    ) {
        User user = currentUserService.getCurrentUser(authentication);
        mfaService.disable(user, request.code());
        return ApiResponse.success("MFA disabled.");
    }

    @GetMapping("/status")
    public ApiResponse<MfaStatusResponse> status(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        return ApiResponse.success(mfaService.status(user));
    }
}
