package com.aurora.backend.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes domain events to Kafka.
 *
 * <p>Publishing is deliberately <strong>best-effort</strong>: the core commerce
 * flows (checkout, payment) must never fail because the event backbone is
 * degraded. Send failures are logged and swallowed, and publishing can be
 * disabled entirely via {@code app.events.enabled=false}.</p>
 */
@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final boolean enabled;

    public DomainEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.events.enabled:true}") boolean enabled
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.enabled = enabled;
    }

    /**
     * Publish an event to a topic, keyed for ordering/partitioning (typically by
     * order number). Never throws.
     *
     * @param topic   destination topic
     * @param key     partition key (e.g. order number); may be {@code null}
     * @param payload event record, serialized as JSON
     */
    public void publish(String topic, String key, Object payload) {
        if (!enabled) {
            log.debug("Event publishing disabled; skipping {} for key {}", topic, key);
            return;
        }

        try {
            kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Failed to publish event to topic {} (key {}): {}", topic, key, ex.getMessage());
                } else if (log.isDebugEnabled()) {
                    log.debug("Published event to topic {} (key {}) at offset {}",
                            topic, key, result.getRecordMetadata().offset());
                }
            });
        } catch (Exception ex) {
            log.warn("Unable to dispatch event to topic {} (key {}): {}", topic, key, ex.getMessage());
        }
    }
}
