package com.aurora.notification.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Default email transport: hands the message to the configured SMTP server
 * (Mailpit in development). Active unless {@code app.notification.email.provider}
 * is set to something other than {@code smtp}.
 */
@Component
@ConditionalOnProperty(name = "app.notification.email.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpMailTransport implements MailTransport {

    private final JavaMailSender mailSender;

    public SmtpMailTransport(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public boolean send(String from, String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        return true;
    }

    @Override
    public String name() {
        return "smtp";
    }
}
