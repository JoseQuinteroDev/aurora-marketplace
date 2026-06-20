package com.aurora.backend.user.controller;

import com.aurora.backend.auth.dto.AuthUserResponse;
import com.aurora.backend.common.api.ApiResponse;
import com.aurora.backend.security.CurrentUserService;
import com.aurora.backend.user.dto.NotificationPreferenceRequest;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.service.AccountService;
import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service account endpoints for the authenticated customer. Sits behind the
 * {@code anyRequest().authenticated()} rule in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;
    private final CurrentUserService currentUserService;

    public AccountController(AccountService accountService, CurrentUserService currentUserService) {
        this.accountService = accountService;
        this.currentUserService = currentUserService;
    }

    @PutMapping("/notification-preference")
    public ApiResponse<AuthUserResponse> updateNotificationPreference(
            @Valid @RequestBody NotificationPreferenceRequest request,
            Authentication authentication
    ) {
        User user = currentUserService.getCurrentUser(authentication);
        AuthUserResponse updated = accountService.updateNotificationPreference(user, request);
        return ApiResponse.success("Notification preference updated.", updated);
    }
}
