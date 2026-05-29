package com.aurora.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.publisher.Mono;

/**
 * Rate-limiting strategy for the gateway.
 *
 * <p>Requests are throttled per client IP. The actual token bucket lives in
 * Redis (shared by the {@code RequestRateLimiter} filter), so the limit holds
 * across multiple gateway instances rather than per-process.</p>
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver clientIpKeyResolver() {
        return exchange -> {
            var remote = exchange.getRequest().getRemoteAddress();
            String key = (remote != null && remote.getAddress() != null)
                    ? remote.getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(key);
        };
    }
}
