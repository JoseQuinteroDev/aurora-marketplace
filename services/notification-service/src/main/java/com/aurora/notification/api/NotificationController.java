package com.aurora.notification.api;

import java.util.List;
import java.util.Map;

import com.aurora.notification.store.NotificationRecord;
import com.aurora.notification.store.NotificationStore;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only window into the notifications this service has processed. Useful
 * for demos and observability without opening Mailpit.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationStore store;

    public NotificationController(NotificationStore store) {
        this.store = store;
    }

    @GetMapping
    public Map<String, Object> recent() {
        List<NotificationRecord> items = store.findRecent();
        return Map.of(
                "count", items.size(),
                "items", items
        );
    }
}
