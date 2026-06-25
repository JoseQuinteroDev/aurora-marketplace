package com.aurora.backend.auth.controller;

import com.aurora.backend.auth.dto.AuthResponse;
import com.aurora.backend.auth.dto.LoginRequest;
import com.aurora.backend.auth.dto.LogoutRequest;
import com.aurora.backend.auth.dto.RefreshRequest;
import com.aurora.backend.auth.dto.RegisterRequest;
import com.aurora.backend.auth.service.AuthService;
import com.aurora.backend.common.api.ApiResponse;
import com.aurora.backend.security.CurrentUserService;
import com.aurora.backend.user.entity.User;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, CurrentUserService currentUserService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully.", response));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("Login successful.", authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success("Token refreshed.", authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody(required = false) LogoutRequest body,
            Authentication authentication
    ) {
        User actor = currentUserService.getCurrentUser(authentication);
        authService.logout(authorizationHeader, actor, body != null ? body.refreshToken() : null);
        return ApiResponse.success("Logout successful.");
    }
}
