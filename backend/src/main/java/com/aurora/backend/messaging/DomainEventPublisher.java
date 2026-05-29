package com.aurora.backend.messaging;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Low-level transport that sends an already-serialized JSON event to Kafka.
 *
 * <p>This is the broker-facing half of the outbox: {@link com.aurora.backend.messaging.outbox.OutboxRelay}
 * calls {@link #publish} for each staged row and uses the boolean result to
 * decide whether to mark the row published or schedule a retry. The send is
 * <strong>synchronous</strong> (it waits for the broker ack) precisely so the
 * relay can react to the outcome — unlike the commerce flow, the relay runs off
 * the request thread, so blocking here is fine.</p>
 */
@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long sendTimeoutMs;

    public DomainEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.events.send-timeout-ms:8000}") long sendTimeoutMs
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    /**
     * Publish a JSON payload to a topic, keyed for partitioning/ordering.
     *
     * @return {@code true} once the broker has acknowledged the record.
     */
    public boolean publish(String topic, String key, String payloadJson) {
        try {
            kafkaTemplate.send(topic, key, payloadJson).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            if (log.isDebugEnabled()) {
                log.debug("Published event to topic {} (key {})", topic, key);
            }
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while publishing to topic {} (key {})", topic, key);
            return false;
        } catch (Exception ex) {
            log.warn("Failed to publish event to topic {} (key {}): {}", topic, key, ex.getMessage());
            return false;
        }
    }
}
