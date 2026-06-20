package com.aurora.notification.email;

/**
 * How an email actually leaves the service. One implementation is active at a
 * time, chosen by {@code app.notification.email.provider}: SMTP (Mailpit in dev)
 * or an HTTP API provider (e.g. Resend) for production. Keeping this behind an
 * interface means the rest of the service never needs to know which is in use.
 */
public interface MailTransport {

    /** @return {@code true} when the message was accepted by the transport. */
    boolean send(String from, String to, String subject, String body);

    /** Short name for logging (e.g. "smtp", "api"). */
    default String name() {
        return getClass().getSimpleName();
    }
}
