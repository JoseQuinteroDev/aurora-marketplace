package com.aurora.notification.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends transactional SMS through the active {@link SmsTransport}. Best-effort
 * and secondary to email: it never throws and never blocks order processing — a
 * customer with no phone, or a disabled SMS channel, simply gets no text.
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final SmsTransport transport;
    private final boolean enabled;
    private final String from;

    public SmsService(
            SmsTransport transport,
            @Value("${app.notification.sms.enabled:true}") boolean enabled,
            @Value("${app.notification.sms.from:Aurora}") String from
    ) {
        this.transport = transport;
        this.enabled = enabled;
        this.from = from;
    }

    /**
     * @return {@code true} when the SMS was accepted by the transport. Returns
     * {@code false} (without throwing) when SMS is disabled, no number is
     * available, or the transport fails.
     */
    public boolean send(String to, String message) {
        if (!enabled) {
            return false;
        }
        if (to == null || to.isBlank()) {
            return false;
        }
        try {
            boolean sent = transport.send(from, to, message);
            if (sent) {
                log.info("Sent SMS to {} via {}", to, transport.name());
            } else {
                log.warn("SMS transport '{}' reported failure for {}", transport.name(), to);
            }
            return sent;
        } catch (Exception ex) {
            log.warn("Failed to send SMS to {} via {}: {}", to, transport.name(), ex.getMessage());
            return false;
        }
    }
}
