package com.aurora.gateway.config;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Mirrors the commerce core's HTTP security-header set onto responses that originate at the
 * gateway itself — the JSON fallbacks (circuit-breaker open), 429 rate-limit rejections,
 * CORS preflights and actuator responses — which never reach the core and so never pick up
 * its headers (OWASP A05). Proxied {@code /api/**} responses already carry the core's
 * headers; {@link HttpHeaders#putIfAbsent} guarantees we never duplicate or override them.
 *
 * <p>Runs at highest precedence so its {@code beforeCommit} hook is registered before the
 * response is written by any downstream filter (including the rate limiter's short-circuit),
 * guaranteeing the headers land on every response. Also stamps {@code Retry-After} on a 429.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ResponseHardeningWebFilter implements WebFilter {

    // This is a JSON API: a lock-everything CSP is appropriate (no HTML is ever served).
    private static final String CSP =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpResponse response = exchange.getResponse();
        response.beforeCommit(() -> {
            HttpHeaders headers = response.getHeaders();
            headers.putIfAbsent("X-Content-Type-Options", List.of("nosniff"));
            headers.putIfAbsent("X-Frame-Options", List.of("DENY"));
            headers.putIfAbsent("Referrer-Policy", List.of("no-referrer"));
            headers.putIfAbsent("Content-Security-Policy", List.of(CSP));
            headers.putIfAbsent("Permissions-Policy", List.of("geolocation=(), camera=(), microphone=()"));
            // HSTS is honoured by browsers only over HTTPS (TLS terminates at the edge);
            // harmless over plain HTTP.
            headers.putIfAbsent("Strict-Transport-Security", List.of("max-age=31536000; includeSubDomains"));

            HttpStatusCode status = response.getStatusCode();
            if (status != null && status.value() == 429 && !headers.containsKey(HttpHeaders.RETRY_AFTER)) {
                // Conservative hint; the Redis token bucket replenishes within ~1s.
                headers.add(HttpHeaders.RETRY_AFTER, "1");
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
