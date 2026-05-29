package com.aurora.gateway.fallback;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/**
 * Graceful degradation responses returned when a downstream service is
 * unavailable and its circuit breaker is open.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/core")
    public Mono<ResponseEntity<Map<String, Object>>> coreFallback() {
        return unavailable("The commerce service is temporarily unavailable. Please try again shortly.");
    }

    @GetMapping("/notifications")
    public Mono<ResponseEntity<Map<String, Object>>> notificationsFallback() {
        return unavailable("The notification service is temporarily unavailable. Please try again shortly.");
    }

    private Mono<ResponseEntity<Map<String, Object>>> unavailable(String message) {
        Map<String, Object> body = Map.of(
                "success", false,
                "error", Map.of(
                        "code", "SERVICE_UNAVAILABLE",
                        "message", message
                ),
                "timestamp", Instant.now().toString()
        );

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));
    }
}
