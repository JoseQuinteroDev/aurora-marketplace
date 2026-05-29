package com.aurora.notification.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Thread-safe, bounded in-memory log of processed notifications. The service
 * owns this data; it is intentionally not shared with any other service.
 */
@Component
public class NotificationStore {

    private static final int MAX_ENTRIES = 500;

    private final Deque<NotificationRecord> records = new ConcurrentLinkedDeque<>();
    private final AtomicInteger size = new AtomicInteger();

    public void add(NotificationRecord record) {
        records.addFirst(record);
        if (size.incrementAndGet() > MAX_ENTRIES) {
            records.pollLast();
            size.decrementAndGet();
        }
    }

    /** Most-recent-first snapshot of processed notifications. */
    public List<NotificationRecord> findRecent() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    public int count() {
        return size.get();
    }
}
