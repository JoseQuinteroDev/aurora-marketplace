package com.aurora.notification.email;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Stored-XSS tripwire (OWASP A03). Email bodies interpolate event-derived
 * fields (customerName, reason, orderNumber) WITHOUT HTML escaping. That is
 * only safe while the transports send <strong>plain text</strong>: a plain-text
 * body renders {@code <script>} literally, so the missing escaping is harmless.
 *
 * <p>The moment a transport is switched to an HTML body (SMTP {@code MimeMessage}
 * with an {@code text/html} part, or the API provider's {@code "html"} field)
 * that latent gap becomes real stored XSS. These assertions are deliberately
 * pinned to the plain-text mechanism so that switch breaks the build and forces
 * whoever flips it to add HTML escaping first.
 */
class MailTransportPlainTextTripwireTest {

    private static final String XSS_PROBE = "<script>alert(1)</script>";

    /**
     * SMTP path must build a {@link SimpleMailMessage} (plain text) and route the
     * body through {@code setText} verbatim — never a {@code MimeMessage}/HTML part.
     * Switching to an HTML body changes the {@code JavaMailSender.send} overload,
     * so {@code send(SimpleMailMessage)} stops being invoked and this fails.
     */
    @Test
    void smtpTransportSendsPlainTextAndDoesNotEscapeOrHtmlEncodeTheBody() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpMailTransport transport = new SmtpMailTransport(mailSender);

        boolean accepted = transport.send("from@aurora.test", "to@aurora.test",
                "Subject " + XSS_PROBE, "Hello " + XSS_PROBE);

        assertThat(accepted).isTrue();

        // Pins the plain-text overload: send(SimpleMailMessage), not send(MimeMessage).
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());

        SimpleMailMessage message = sent.getValue();
        // Body carried verbatim via setText — the probe is NOT HTML-escaped, which is
        // exactly why this MUST stay plain text. If it were ever escaped here, that
        // would hint someone started treating the body as HTML.
        assertThat(message.getText())
                .as("SMTP body must be the raw plain-text string (setText), not HTML")
                .isEqualTo("Hello " + XSS_PROBE);
        assertThat(message.getText()).doesNotContain("&lt;script&gt;");
        assertThat(message.getSubject()).isEqualTo("Subject " + XSS_PROBE);
        assertThat(message.getFrom()).isEqualTo("from@aurora.test");
    }

    /**
     * API path must place the body under the JSON {@code "text"} key, never
     * {@code "html"}. Captures the real outbound request with an in-process HTTP
     * stub (JDK {@link HttpServer} — no Docker, no network), then inspects the
     * JSON. Flipping the field to {@code "html"} fails both assertions below.
     */
    @Test
    void apiTransportPutsTheBodyUnderTheTextKeyNeverHtml() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            try {
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] ok = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, ok.length);
                exchange.getResponseBody().write(ok);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            ApiMailTransport transport = new ApiMailTransport(baseUrl, "test-api-key");

            boolean accepted = transport.send("from@aurora.test", "to@aurora.test",
                    "Subject " + XSS_PROBE, "Hello " + XSS_PROBE);

            assertThat(accepted).isTrue();

            JsonNode payload = new ObjectMapper().readTree(capturedBody.get());
            // The contract under test: plain-text body lives under "text".
            assertThat(payload.has("text"))
                    .as("API payload must carry the body under the plain-text \"text\" field")
                    .isTrue();
            assertThat(payload.get("text").asText()).isEqualTo("Hello " + XSS_PROBE);
            // The tripwire: an "html" field would make this stored XSS until the body
            // is escaped. Forbid it here so the switch cannot land silently.
            assertThat(payload.has("html"))
                    .as("API payload must NOT send an \"html\" body without HTML escaping (stored-XSS guard)")
                    .isFalse();
        } finally {
            server.stop(0);
        }
    }
}
