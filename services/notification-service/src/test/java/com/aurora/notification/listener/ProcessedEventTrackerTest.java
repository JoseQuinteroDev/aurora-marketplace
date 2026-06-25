package com.aurora.notification.listener;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProcessedEventTracker} — the in-memory idempotency key
 * store that makes the consumer safe under Kafka's at-least-once redelivery.
 */
class ProcessedEventTrackerTest {

    private final ProcessedEventTracker tracker = new ProcessedEventTracker();

    @Test
    void anUnseenEventIsNotADuplicate() {
        assertThat(tracker.alreadyProcessed("evt-1")).isFalse();
    }

    @Test
    void aMarkedEventIsThenSeenAsADuplicate() {
        tracker.markProcessed("evt-1");

        assertThat(tracker.alreadyProcessed("evt-1")).isTrue();
        assertThat(tracker.alreadyProcessed("evt-2")).isFalse();
    }

    @Test
    void blankOrNullIdsAreNeverDeduped() {
        // No id to dedupe on → always let the record through (and marking is a no-op).
        tracker.markProcessed(null);
        tracker.markProcessed("   ");

        assertThat(tracker.alreadyProcessed(null)).isFalse();
        assertThat(tracker.alreadyProcessed("")).isFalse();
        assertThat(tracker.alreadyProcessed("   ")).isFalse();
    }
}
