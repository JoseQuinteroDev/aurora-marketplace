package com.aurora.notification.listener;

import java.time.Instant;
import java.util.UUID;

import com.aurora.notification.email.EmailService;
import com.aurora.notification.event.OrderCreatedEvent;
import com.aurora.notification.event.PaymentConfirmedEvent;
import com.aurora.notification.event.PaymentFailedEvent;
import com.aurora.notification.sms.SmsService;
import com.aurora.notification.store.NotificationRecord;
import com.aurora.notification.store.NotificationStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to Aurora domain events and turns them into a customer notification
 * delivered on the channel the customer chose (carried on the event as
 * {@code notificationChannel}). The choice is exclusive: a customer is notified
 * by email <em>or</em> SMS, never both.
 *
 * <p>Two reliability properties matter here:</p>
 * <ul>
 *   <li><b>Idempotent:</b> each event id is delivered once even if Kafka
 *       redelivers the record (see {@link ProcessedEventTracker}).</li>
 *   <li><b>Resilient:</b> a malformed payload is non-retryable and goes straight
 *       to the dead-letter topic; a transient delivery failure on the chosen
 *       channel propagates to the configured error handler (retried, then
 *       dead-lettered). The event id is marked processed only after the chosen
 *       channel actually accepts the message.</li>
 * </ul>
 *
 * <p>The channel is read defensively: SMS is used only when it was selected
 * <em>and</em> a phone is present, so an unrecognised channel or a missing number
 * safely falls back to email rather than failing delivery.</p>
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private static final String TOPIC_ORDER_CREATED = "aurora.orders.created";
    private static final String TOPIC_PAYMENT_CONFIRMED = "aurora.payments.confirmed";
    private static final String TOPIC_PAYMENT_FAILED = "aurora.payments.failed";

    private static final String CHANNEL_SMS = "SMS";

    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final SmsService smsService;
    private final NotificationStore store;
    private final ProcessedEventTracker processedEvents;

    public NotificationListener(ObjectMapper objectMapper, EmailService emailService, SmsService smsService,
                                NotificationStore store, ProcessedEventTracker processedEvents) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
        this.smsService = smsService;
        this.store = store;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(topics = TOPIC_ORDER_CREATED)
    public void onOrderCreated(String payload) {
        OrderCreatedEvent event = parse(payload, OrderCreatedEvent.class, TOPIC_ORDER_CREATED);
        if (isDuplicate(event.eventId(), TOPIC_ORDER_CREATED)) {
            return;
        }

        String subject = "Your Aurora order " + event.orderNumber() + " is confirmed";
        String emailBody = """
                Hi %s,

                Thanks for shopping with Aurora Marketplace!

                We've received your order %s with %d item(s).
                Order total: %s %s

                We'll email you again as soon as your payment is confirmed.

                — The Aurora Marketplace team
                """.formatted(
                event.customerName(),
                event.orderNumber(),
                event.itemCount(),
                event.total(),
                event.currency()
        );
        String smsText = "Aurora Marketplace: order %s confirmed — %d item(s), total %s %s. Thank you, %s!"
                .formatted(
                        event.orderNumber(),
                        event.itemCount(),
                        event.total(),
                        event.currency(),
                        event.customerName()
                );

        deliver(event.eventId(), "ORDER_CREATED", event.notificationChannel(),
                event.customerEmail(), event.customerPhone(), subject, emailBody, smsText, event.orderNumber());
    }

    @KafkaListener(topics = TOPIC_PAYMENT_CONFIRMED)
    public void onPaymentConfirmed(String payload) {
        PaymentConfirmedEvent event = parse(payload, PaymentConfirmedEvent.class, TOPIC_PAYMENT_CONFIRMED);
        if (isDuplicate(event.eventId(), TOPIC_PAYMENT_CONFIRMED)) {
            return;
        }

        String subject = "Payment received for order " + event.orderNumber();
        String emailBody = """
                Hi %s,

                Good news — we've received your payment of %s %s for order %s.

                Your order is now being prepared. You can track its status from
                your Aurora account at any time.

                — The Aurora Marketplace team
                """.formatted(
                event.customerName(),
                event.amount(),
                event.currency(),
                event.orderNumber()
        );
        String smsText = "Aurora Marketplace: payment of %s %s received for order %s. Your order is being prepared. Thanks, %s!"
                .formatted(
                        event.amount(),
                        event.currency(),
                        event.orderNumber(),
                        event.customerName()
                );

        deliver(event.eventId(), "PAYMENT_CONFIRMED", event.notificationChannel(),
                event.customerEmail(), event.customerPhone(), subject, emailBody, smsText, event.orderNumber());
    }

    @KafkaListener(topics = TOPIC_PAYMENT_FAILED)
    public void onPaymentFailed(String payload) {
        PaymentFailedEvent event = parse(payload, PaymentFailedEvent.class, TOPIC_PAYMENT_FAILED);
        if (isDuplicate(event.eventId(), TOPIC_PAYMENT_FAILED)) {
            return;
        }

        String subject = "Action needed: payment failed for order " + event.orderNumber();
        String emailBody = """
                Hi %s,

                We weren't able to process your payment of %s %s for order %s.

                Reason: %s

                No charge was made. Please try again from your Aurora account to
                complete your purchase.

                — The Aurora Marketplace team
                """.formatted(
                event.customerName(),
                event.amount(),
                event.currency(),
                event.orderNumber(),
                event.reason()
        );
        String smsText = "Aurora Marketplace: payment of %s %s for order %s failed (%s). No charge was made — please try again from your account."
                .formatted(
                        event.amount(),
                        event.currency(),
                        event.orderNumber(),
                        event.reason()
                );

        deliver(event.eventId(), "PAYMENT_FAILED", event.notificationChannel(),
                event.customerEmail(), event.customerPhone(), subject, emailBody, smsText, event.orderNumber());
    }

    private boolean isDuplicate(String eventId, String topic) {
        if (processedEvents.alreadyProcessed(eventId)) {
            log.info("Skipping duplicate event {} on topic {}", eventId, topic);
            return true;
        }
        return false;
    }

    /**
     * Delivers the notification on the customer's chosen channel and records the
     * outcome. A delivery failure is rethrown so the Kafka error handler can retry
     * and ultimately dead-letter it — the event id is marked processed only once
     * delivery actually succeeds.
     */
    private void deliver(String eventId, String type, String channel,
                         String email, String phone, String subject,
                         String emailBody, String smsText, String orderNumber) {
        if (wantsSms(channel, phone)) {
            boolean sent = smsService.send(phone, smsText);
            if (!sent) {
                throw new IllegalStateException("SMS delivery failed for event " + eventId + " (" + type + ")");
            }
            // The dev "log" transport only writes to the log, so record LOGGED rather than SENT.
            String status = "log".equals(smsService.transportName()) ? "LOGGED" : "SENT";
            record(type, "SMS", phone, subject, orderNumber, status);
        } else {
            boolean sent = emailService.send(email, subject, emailBody);
            if (!sent) {
                throw new IllegalStateException("Email delivery failed for event " + eventId + " (" + type + ")");
            }
            record(type, "EMAIL", email, subject, orderNumber, "SENT");
        }
        processedEvents.markProcessed(eventId);
    }

    /** SMS is the delivery channel only when it was chosen and a number is available to text. */
    private boolean wantsSms(String channel, String phone) {
        return CHANNEL_SMS.equalsIgnoreCase(channel) && phone != null && !phone.isBlank();
    }

    private void record(String type, String channel, String recipient,
                        String subject, String orderNumber, String status) {
        store.add(new NotificationRecord(
                UUID.randomUUID().toString(),
                type,
                channel,
                recipient,
                subject,
                orderNumber,
                status,
                Instant.now()
        ));
    }

    private <T> T parse(String payload, Class<T> type, String topic) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (Exception ex) {
            // Unrecoverable: no number of retries will fix a malformed payload.
            // Signal the error handler to dead-letter it immediately.
            throw new NonRetryableEventException("Malformed event on topic " + topic, ex);
        }
    }
}
