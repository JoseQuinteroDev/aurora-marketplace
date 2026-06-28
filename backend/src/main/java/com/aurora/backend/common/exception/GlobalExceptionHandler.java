package com.aurora.backend.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NotFoundException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(error ->
                validationErrors.put(error.getField(), resolveFieldErrorMessage(error))
        );

        exception.getBindingResult().getGlobalErrors().forEach(error ->
                validationErrors.put(error.getObjectName(), error.getDefaultMessage())
        );

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation failed.",
                request.getRequestURI(),
                validationErrors
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new LinkedHashMap<>();

        exception.getConstraintViolations().forEach(violation ->
                validationErrors.put(violation.getPropertyPath().toString(), violation.getMessage())
        );

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation failed.",
                request.getRequestURI(),
                validationErrors
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                "The request is invalid.",
                request
        );
    }

    @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrencyConflict(
            org.springframework.dao.ConcurrencyFailureException exception,
            HttpServletRequest request
    ) {
        // Any concurrency loss (OWASP A04): an optimistic-lock failure (@Version), or a
        // pessimistic-lock acquisition failure / DB deadlock (e.g. contended checkout). All are
        // transient, not faults — surface a clean 409 the client can safely retry, never a 500.
        // Logged at warn (a burst can indicate contention or an attack).
        log.warn("Concurrency conflict ({}) on {} {}",
                exception.getClass().getSimpleName(), request.getMethod(), request.getRequestURI());
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION",
                "The resource was modified concurrently. Please retry.",
                request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "The request method is not supported.",
                request
        );
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException exception,
            HttpServletRequest request
    ) {
        // A method-security (@PreAuthorize) denial that propagates into the dispatcher must
        // surface as a clean 403 — not the catch-all 500 below (OWASP A01 defense-in-depth).
        // URL-level denials are already turned into 403 by Spring Security's filter chain before
        // this advice ever runs; this covers the method-authorization backup layer on the core.
        // Uses the same FORBIDDEN code as the filter-chain accessDeniedHandler so both 403 sources
        // share one contract, and logs the denial as a security signal (parity with that handler).
        log.warn("Access denied (method security) on {} {}", request.getMethod(), request.getRequestURI());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        // A 500 is either a real fault or an exploitation attempt — either way it must
        // leave a trace. Logged with the full stack (and trace/span id from the MDC pattern);
        // clients still get only the generic message below.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), exception);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred.",
                request
        );
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.of(status, code, message, request.getRequestURI());
        return ResponseEntity.status(status).body(response);
    }

    private String resolveFieldErrorMessage(FieldError error) {
        return error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage();
    }
}
