package com.aurora.backend.security.jwt;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT secret is required.")
        @Size(min = 32, message = "JWT secret must be at least 32 characters.")
        String secret,

        @Min(value = 1, message = "JWT expiration must be at least 1 minute.")
        long expirationMinutes
) {
}
