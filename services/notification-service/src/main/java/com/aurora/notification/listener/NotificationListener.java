package com.aurora.notification.listener;

import java.time.Instant;
import java.util.UUID;

import com.aurora.notification.email.EmailService;
import com.aurora.notification.event.OrderCreatedEvent;
import com.aurora.notification.event.PaymentConfirmedEvent;
import com.aurora.notification.event.PaymentFailedEvent;
import com.aurora.notification.store.NotificationRecord;
import com.aurora.notification.store.NotificationStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to Aurora domain events and turns them into customer emails.
 *
 * <p>Events are consumed as raw JSON strings and mapped locally, which keeps
 * this service fully decoupled from the producer's classes.</p>
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private static final String TOPIC_ORDER_CREATED = "aurora.orders.created";
    private static final String TOPIC_PAYMENT_CONFIRMED = "aurora.payments.confirmed";
    private static final String TOPIC_PAYMENT_FAILED = "aurora.payments.failed";

    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final NotificationStore store;

    public NotificationListener(ObjectMapper objectMapper, EmailService emailService, NotificationStore store) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
        this.store = store;
    }

    @KafkaListener(topics = TOPIC_ORDER_CREATED)
    public void onOrderCreated(String payload) {
        OrderCreatedEvent event = parse(payload, OrderCreatedEvent.class, TOPIC_ORDER_CREATED);
        if (event == null) {
            return;
        }

        String subject = "Your Aurora order " + event.orderNumber() + " is confirmed";
        String body = """
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

        deliver("ORDER_CREATED", event.customerEmail(), subject, event.orderNumber(), body);
    }

    @KafkaListener(topics = TOPIC_PAYMENT_CONFIRMED)
    public void onPaymentConfirmed(String payload) {
        PaymentConfirmedEvent event = parse(payload, PaymentConfirmedEvent.class, TOPIC_PAYMENT_CONFIRMED);
        if (event == null) {
            return;
        }

        String subject = "Payment received for order " + event.orderNumber();
        String body = """
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

        deliver("PAYMENT_CONFIRMED", event.customerEmail(), subject, event.orderNumber(), body);
    }

    @KafkaListener(topics = TOPIC_PAYMENT_FAILED)
    public void onPaymentFailed(String payload) {
        PaymentFailedEvent event = parse(payload, PaymentFailedEvent.class, TOPIC_PAYMENT_FAILED);
        if (event == null) {
            return;
        }

        String subject = "Action needed: payment failed for order " + event.orderNumber();
        String body = """
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

        deliver("PAYMENT_FAILED", event.customerEmail(), subject, event.orderNumber(), body);
    }

    private void deliver(String type, String recipient, String subject, String orderNumber, String body) {
        boolean sent = emailService.send(recipient, subject, body);
        store.add(new NotificationRecord(
                UUID.randomUUID().toString(),
                type,
                "EMAIL",
                recipient,
                subject,
                orderNumber,
                sent ? "SENT" : "FAILED",
                Instant.now()
        ));
    }

    private <T> T parse(String payload, Class<T> type, String topic) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (Exception ex) {
            log.warn("Discarding malformed event on topic {}: {}", topic, ex.getMessage());
            return null;
        }
    }
}
