package com.aurora.notification.listener;

/**
 * Marks an event that can never succeed no matter how often it is retried —
 * typically a malformed/undeserializable payload (a "poison" message). The
 * error handler routes these straight to the dead-letter topic instead of
 * wasting the retry budget on them.
 */
public class NonRetryableEventException extends RuntimeException {

    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
