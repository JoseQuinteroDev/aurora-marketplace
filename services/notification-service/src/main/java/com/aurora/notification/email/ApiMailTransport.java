package com.aurora.notification.email;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP email transport for production, calling a transactional-email API
 * (Resend-compatible: {@code POST /emails} with a {@code Bearer} key). Active
 * when {@code app.notification.email.provider=api}. The endpoint is a fixed,
 * configured host — never request-derived — so this is not an SSRF surface.
 */
@Component
@ConditionalOnProperty(name = "app.notification.email.provider", havingValue = "api")
public class ApiMailTransport implements MailTransport {

    private static final Logger log = LoggerFactory.getLogger(ApiMailTransport.class);

    private final RestClient client;
    private final String apiKey;

    public ApiMailTransport(
            @Value("${app.notification.email.api.base-url:https://api.resend.com}") String baseUrl,
            @Value("${app.notification.email.api.key:}") String apiKey
    ) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public boolean send(String from, String to, String subject, String body) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Email provider 'api' is selected but app.notification.email.api.key is not set — cannot send.");
            return false;
        }
        // RestClient.retrieve() throws on a non-2xx response, which the caller
        // (EmailService) turns into a delivery failure → Kafka retry/DLT.
        client.post()
                .uri("/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "from", from,
                        "to", List.of(to),
                        "subject", subject,
                        "text", body
                ))
                .retrieve()
                .toBodilessEntity();
        return true;
    }

    @Override
    public String name() {
        return "api";
    }
}
