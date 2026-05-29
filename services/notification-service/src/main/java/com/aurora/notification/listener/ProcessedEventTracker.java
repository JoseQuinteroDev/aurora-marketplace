package com.aurora.notification.listener;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Remembers which event ids have already been handled, so redeliveries (Kafka is
 * at-least-once: a rebalance or restart can replay a record) don't send the same
 * email twice. This makes the consumer <strong>idempotent</strong>.
 *
 * <p>An id is marked as processed only <em>after</em> a successful delivery, so a
 * record that failed and is being retried is not mistaken for a duplicate.
 * Backed by a bounded LRU map — recent ids are what matter for deduplication and
 * the cap keeps memory flat. A production system would persist this (e.g. a
 * unique constraint on {@code event_id}); in-memory keeps the service true to
 * its "owns its own data, shares nothing" design.</p>
 */
@Component
public class ProcessedEventTracker {

    private static final int MAX_TRACKED = 50_000;

    private final Map<String, Boolean> processed = Collections.synchronizedMap(
            new LinkedHashMap<>(1024, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_TRACKED;
                }
            });

    /** @return {@code true} if this id was already delivered and should be skipped. */
    public boolean alreadyProcessed(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false; // nothing to dedupe on — let it through
        }
        return processed.containsKey(eventId);
    }

    /** Record an id as successfully delivered. */
    public void markProcessed(String eventId) {
        if (eventId != null && !eventId.isBlank()) {
            processed.put(eventId, Boolean.TRUE);
        }
    }
}
