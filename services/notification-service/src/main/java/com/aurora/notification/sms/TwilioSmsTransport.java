package com.aurora.notification.sms;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Production SMS transport: sends via Twilio's REST API
 * ({@code POST /2010-04-01/Accounts/{sid}/Messages.json}, HTTP Basic auth with
 * the account SID + auth token). Active when
 * {@code app.notification.sms.provider=twilio}. The endpoint is a fixed,
 * configured host — never request-derived.
 */
@Component
@ConditionalOnProperty(name = "app.notification.sms.provider", havingValue = "twilio")
public class TwilioSmsTransport implements SmsTransport {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsTransport.class);

    private final RestClient client;
    private final String accountSid;
    private final String authToken;

    public TwilioSmsTransport(
            @Value("${app.notification.sms.twilio.base-url:https://api.twilio.com}") String baseUrl,
            @Value("${app.notification.sms.twilio.account-sid:}") String accountSid,
            @Value("${app.notification.sms.twilio.auth-token:}") String authToken
    ) {
        // Bounded timeouts so a hung provider surfaces as an exception (SMS is
        // best-effort, so SmsService swallows it) rather than blocking the consumer.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.accountSid = accountSid;
        this.authToken = authToken;
    }

    @Override
    public boolean send(String from, String to, String message) {
        if (accountSid == null || accountSid.isBlank() || authToken == null || authToken.isBlank()) {
            log.warn("SMS provider 'twilio' is selected but account-sid/auth-token are not set — cannot send.");
            return false;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", from);
        form.add("To", to);
        form.add("Body", message);

        // retrieve() throws on a non-2xx response → the caller treats it as a failure.
        client.post()
                .uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                .headers(headers -> headers.setBasicAuth(accountSid, authToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
        return true;
    }

    @Override
    public String name() {
        return "twilio";
    }
}
