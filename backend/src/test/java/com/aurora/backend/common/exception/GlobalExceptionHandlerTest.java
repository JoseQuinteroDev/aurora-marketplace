package com.aurora.backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Directly exercises every {@link GlobalExceptionHandler} {@code @ExceptionHandler} mapping
 * (the wider suite only covered the 400 validation path incidentally). Each test instantiates
 * the handler and invokes one mapping with a representative exception and a mocked request,
 * asserting the HTTP status, the {@link ErrorResponse} {@code code}, and the stable envelope
 * shape ({@code path}, numeric {@code status}, reason-phrase {@code error}, non-null timestamp,
 * non-null validationErrors).
 *
 * <p>The security-critical guard is the catch-all: a 500 must surface a generic message and
 * must NOT leak the raw exception text/stacktrace (OWASP A09/A05 — information disclosure).
 * Pure unit test — no Spring context, no Docker.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest requestFor(String method, String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getMethod()).thenReturn(method);
        return request;
    }

    @Test
    void notFoundMapsTo404WithNotFoundCode() {
        HttpServletRequest request = requestFor("GET", "/api/products/missing");

        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new NotFoundException("Product", 42L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.error()).isEqualTo(HttpStatus.NOT_FOUND.getReasonPhrase());
        assertThat(body.code()).isEqualTo("NOT_FOUND");
        assertThat(body.message()).contains("Product").contains("42");
        assertThat(body.path()).isEqualTo("/api/products/missing");
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.validationErrors()).isNotNull().isEmpty();
    }

    @Test
    void businessExceptionPreservesItsCustomStatusAndCode() {
        // A BusinessException can carry any status/code; the handler must echo them verbatim,
        // not collapse everything to 400.
        BusinessException exception =
                new BusinessException(HttpStatus.CONFLICT, "COUPON_EXPIRED", "The coupon has expired.");
        HttpServletRequest request = requestFor("POST", "/api/cart/coupon");

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(body.code()).isEqualTo("COUPON_EXPIRED");
        assertThat(body.message()).isEqualTo("The coupon has expired.");
        assertThat(body.path()).isEqualTo("/api/cart/coupon");
    }

    @Test
    void businessExceptionDefaultsTo400BusinessError() {
        // The bare-message constructor defaults to 400 / BUSINESS_ERROR.
        HttpServletRequest request = requestFor("POST", "/api/checkout");

        ResponseEntity<ErrorResponse> response =
                handler.handleBusinessException(new BusinessException("Cart is empty."), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.code()).isEqualTo("BUSINESS_ERROR");
        assertThat(body.message()).isEqualTo("Cart is empty.");
    }

    @Test
    void concurrencyFailureMapsTo409ConcurrentModification() {
        // ObjectOptimisticLockingFailureException is a ConcurrencyFailureException; a lost-update
        // conflict must be a retryable 409, never a 500 (OWASP A04).
        ObjectOptimisticLockingFailureException exception =
                new ObjectOptimisticLockingFailureException("Order", 7L);
        HttpServletRequest request = requestFor("PATCH", "/api/orders/7");

        ResponseEntity<ErrorResponse> response = handler.handleConcurrencyConflict(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(409);
        assertThat(body.code()).isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(body.path()).isEqualTo("/api/orders/7");
        // Must not echo the raw persistence-layer message back to the client.
        assertThat(body.message()).isEqualTo("The resource was modified concurrently. Please retry.");
    }

    @Test
    void accessDeniedMapsTo403Forbidden() {
        // A method-security denial reaching this advice must be a clean 403, not the catch-all 500
        // (OWASP A01 defense-in-depth).
        AccessDeniedException exception = new AccessDeniedException("Access is denied");
        HttpServletRequest request = requestFor("DELETE", "/api/admin/products/9");

        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(403);
        assertThat(body.code()).isEqualTo("FORBIDDEN");
        assertThat(body.message()).isEqualTo("Access is denied.");
        assertThat(body.path()).isEqualTo("/api/admin/products/9");
    }

    @Test
    void unexpectedExceptionMapsTo500WithGenericNonLeakingMessage() {
        // The catch-all must hide internals: the body must carry the generic message and the
        // INTERNAL_SERVER_ERROR code, and must NOT expose the raw exception's message
        // (OWASP A09/A05 — information disclosure).
        String secretLeak = "java.sql.SQLException: password=hunter2 at com.aurora.Secret.line(42)";
        RuntimeException exception = new IllegalStateException(secretLeak);
        HttpServletRequest request = requestFor("GET", "/api/orders");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(500);
        assertThat(body.code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(body.message()).isEqualTo("An unexpected error occurred.");
        // The raw exception detail (and any class name fragment of it) must never reach the client.
        assertThat(body.message()).doesNotContain(secretLeak);
        assertThat(body.message()).doesNotContain("hunter2");
        assertThat(body.message()).doesNotContain("SQLException");
        assertThat(body.path()).isEqualTo("/api/orders");
        assertThat(body.validationErrors()).isNotNull().isEmpty();
    }
}
