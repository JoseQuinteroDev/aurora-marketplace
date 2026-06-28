package com.aurora.backend.security.password;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires the {@link PasswordBreachChecker} bean. The breach check is enabled by default
 * (OWASP A07 credential hygiene) and can be disabled per environment via
 * {@code app.security.breached-password.enabled=false} — handy for fully offline/air-gapped
 * deployments. The remote client uses tight connect/read timeouts so a slow corpus can't
 * stall registration; the checker itself fails open on any error.
 */
@Configuration
public class PasswordBreachConfig {

    private static final Logger log = LoggerFactory.getLogger(PasswordBreachConfig.class);

    @Bean
    PasswordBreachChecker passwordBreachChecker(
            @Value("${app.security.breached-password.enabled:true}") boolean enabled,
            @Value("${app.security.breached-password.api-base-url:https://api.pwnedpasswords.com}") String baseUrl,
            @Value("${app.security.breached-password.timeout-ms:2500}") int timeoutMs) {

        if (!enabled) {
            log.info("Breached-password check is DISABLED (app.security.breached-password.enabled=false).");
            return new HibpPasswordBreachChecker(false, prefix -> "");
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .build();

        HibpPasswordBreachChecker.RangeClient rangeClient = prefix5 -> restClient.get()
                .uri("/range/{prefix}", prefix5)
                // Response padding defeats traffic analysis of the response size; padded
                // decoy entries carry a count of 0 and are ignored by the matcher.
                .header("Add-Padding", "true")
                .retrieve()
                .body(String.class);

        log.info("Breached-password check ENABLED (corpus={}).", baseUrl);
        return new HibpPasswordBreachChecker(true, rangeClient);
    }
}
