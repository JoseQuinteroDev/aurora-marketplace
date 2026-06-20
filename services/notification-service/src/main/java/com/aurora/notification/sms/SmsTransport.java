package com.aurora.notification.sms;

/**
 * How an SMS actually leaves the service. One implementation is active at a time,
 * chosen by {@code app.notification.sms.provider}: a logging transport for
 * development (writes the message to the log) or Twilio for production.
 */
public interface SmsTransport {

    /** @return {@code true} when the message was accepted by the transport. */
    boolean send(String from, String to, String message);

    /** Short name for logging (e.g. "log", "twilio"). */
    default String name() {
        return getClass().getSimpleName();
    }
}
