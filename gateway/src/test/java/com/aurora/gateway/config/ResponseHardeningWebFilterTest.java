package com.aurora.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the gateway response-hardening filter (OWASP A05). No Redis / Spring
 * context — a {@link MockServerWebExchange} drives the filter and a stub chain commits the
 * response, firing the {@code beforeCommit} hook so we can assert the resulting headers.
 */
class ResponseHardeningWebFilterTest {

    private final ResponseHardeningWebFilter filter = new ResponseHardeningWebFilter();

    @Test
    void addsTheCoreSecurityHeaderSetToAGatewayOriginatedResponse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/fallback/core"));
        WebFilterChain chain = ex -> ex.getResponse().setComplete();

        filter.filter(exchange, chain).block();

        HttpHeaders h = exchange.getResponse().getHeaders();
        assertThat(h.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(h.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(h.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(h.getFirst("Content-Security-Policy")).contains("default-src 'none'");
        assertThat(h.getFirst("Permissions-Policy")).contains("geolocation=()");
        assertThat(h.getFirst("Strict-Transport-Security")).contains("max-age=31536000");
    }

    @Test
    void doesNotOverrideAHeaderAlreadySetByTheDownstreamCore() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products"));
        WebFilterChain chain = ex -> {
            ex.getResponse().getHeaders().set("Content-Security-Policy", "core-policy");
            return ex.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("Content-Security-Policy"))
                .isEqualTo("core-policy");
    }

    @Test
    void stampsRetryAfterOnA429() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/login"));
        WebFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return ex.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
    }

    @Test
    void doesNotStampRetryAfterOnASuccessfulResponse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products"));
        WebFilterChain chain = ex -> ex.getResponse().setComplete();

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().containsKey(HttpHeaders.RETRY_AFTER)).isFalse();
    }
}
