package com.aurora.backend.user.notification;

/**
 * The channel a customer prefers for transactional notifications. The choice is
 * exclusive — a customer is notified by one channel, not several. SMS is only
 * deliverable when a phone number is on file; {@code User} resolves that fallback
 * so the rest of the system can trust the effective channel.
 */
public enum NotificationChannel {
    EMAIL,
    SMS
}
