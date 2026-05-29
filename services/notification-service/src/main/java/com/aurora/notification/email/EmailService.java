package com.aurora.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails through the configured SMTP server (Mailpit in
 * development). Sending is best-effort: a failure is logged and reported back
 * so the caller can record the outcome, but it never crashes the consumer.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.notification.from:Aurora Marketplace <no-reply@aurora.dev>}") String from
    ) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /**
     * @return {@code true} when the email was handed to the SMTP server.
     */
    public boolean send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Sent '{}' email to {}", subject, to);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to send '{}' email to {}: {}", subject, to, ex.getMessage());
            return false;
        }
    }
}
