package com.aurora.backend.common.exception;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Map<String, String> validationErrors
) {

    public static ErrorResponse of(HttpStatus status, String code, String message, String path) {
        return of(status, code, message, path, Map.of());
    }

    public static ErrorResponse of(
            HttpStatus status,
            String code,
            String message,
            String path,
            Map<String, String> validationErrors
    ) {
        return new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                path,
                validationErrors == null ? Map.of() : Map.copyOf(validationErrors)
        );
    }
}
