package com.aurora.backend.messaging.outbox;

/** Lifecycle of an outbox row. */
public enum OutboxStatus {

    /** Recorded in the business transaction, not yet on the broker. */
    PENDING,

    /** Successfully published to Kafka. */
    PUBLISHED,

    /** Exhausted the relay's retry budget; parked for inspection. */
    FAILED
}
