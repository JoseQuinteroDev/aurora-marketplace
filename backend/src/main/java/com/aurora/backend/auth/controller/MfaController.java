package com.aurora.backend.auth.controller;

import com.aurora.backend.auth.dto.MfaCodeRequest;
import com.aurora.backend.auth.dto.MfaEnrollResponse;
import com.aurora.backend.auth.dto.MfaStatusResponse;
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
 * MFA self-service enrollment for the authenticated account holder (OWASP A07). All four endpoints
 * require authentication — they are NOT in {@code SecurityConfig}'s public permit-list, so the
 * {@code anyRequest().authenticated()} rule applies. The public {@code /mfa/verify} login endpoint
 * is a separate later increment and is intentionally not defined here.
 *
 * <p>Thin adapter: the user is resolved from the security context via {@link CurrentUserService} and
 * never trusted from the request body; business rules live in {@link MfaService}.
 */
@RestController
@RequestMapping("/api/auth/mfa")
public class MfaController {

    private final MfaService mfaService;
    private final CurrentUserService currentUserService;

    public MfaController(MfaService mfaService, CurrentUserService currentUserService) {
        this.mfaService = mfaService;
        this.currentUserService = currentUserService;
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
