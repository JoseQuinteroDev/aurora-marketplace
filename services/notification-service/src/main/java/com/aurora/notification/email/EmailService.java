package com.aurora.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails through the active {@link MailTransport} (SMTP in
 * development, an HTTP API provider in production — selected by
 * {@code app.notification.email.provider}). Sending is best-effort: a failure is
 * logged and reported back so the caller can record the outcome and let Kafka
 * retry/dead-letter it, but it never crashes the consumer.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final MailTransport transport;
    private final String from;

    public EmailService(
            MailTransport transport,
            @Value("${app.notification.from:Aurora Marketplace <no-reply@aurora.dev>}") String from
    ) {
        this.transport = transport;
        this.from = from;
    }

    /**
     * @return {@code true} when the email was accepted by the transport.
     */
    public boolean send(String to, String subject, String body) {
        try {
            boolean sent = transport.send(from, to, subject, body);
            if (sent) {
                log.info("Sent '{}' email to {} via {}", subject, to, transport.name());
            } else {
                log.warn("Email transport '{}' reported failure for '{}' to {}", transport.name(), subject, to);
            }
            return sent;
        } catch (Exception ex) {
            log.warn("Failed to send '{}' email to {} via {}: {}", subject, to, transport.name(), ex.getMessage());
            return false;
        }
    }
}
