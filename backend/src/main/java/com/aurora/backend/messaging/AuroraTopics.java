package com.aurora.backend.messaging;

/**
 * Canonical Kafka topic names for Aurora Marketplace domain events.
 *
 * <p>Topics follow the {@code aurora.<aggregate>.<event>} convention. They are
 * shared, by contract, with every consumer (e.g. notification-service). The
 * contract is the topic name plus the JSON shape of the event record — there is
 * intentionally no shared jar, so each service owns its own deserialization.</p>
 */
public final class AuroraTopics {

    public static final String ORDER_CREATED = "aurora.orders.created";
    public static final String PAYMENT_CONFIRMED = "aurora.payments.confirmed";
    public static final String PAYMENT_FAILED = "aurora.payments.failed";
    public static final String PASSWORD_RESET_REQUESTED = "aurora.auth.password-reset-requested";

    private AuroraTopics() {
    }
}
