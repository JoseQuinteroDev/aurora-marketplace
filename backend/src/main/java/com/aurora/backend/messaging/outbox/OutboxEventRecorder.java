package com.aurora.backend.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Records domain events into the {@link OutboxEvent transactional outbox}.
 *
 * <p>This is the entry point business services call instead of talking to Kafka
 * directly. Because the row is written through JPA, it joins the caller's
 * transaction: if checkout/payment rolls back, the event is never recorded; if
 * it commits, {@link OutboxRelay} guarantees the event reaches the broker
 * eventually (at-least-once). Recording is a fast local insert and never
 * touches the network, so it cannot slow down or break the commerce flow.</p>
 */
@Component
public class OutboxEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventRecorder.class);

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public OutboxEventRecorder(
            OutboxEventRepository repository,
            ObjectMapper objectMapper,
            @Value("${app.events.enabled:true}") boolean enabled
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    /**
     * Stage an event for publication, in the current transaction.
     *
     * @param aggregateType aggregate the event belongs to (e.g. {@code ORDER})
     * @param aggregateId   business id of the aggregate (e.g. order number)
     * @param eventType     logical event name (e.g. {@code ORDER_CREATED})
     * @param topic         destination Kafka topic
     * @param messageKey    partition key (typically the order number)
     * @param payload       event record; serialized to JSON for storage
     */
    public void record(String aggregateType, String aggregateId, String eventType,
                       String topic, String messageKey, Object payload) {
        if (!enabled) {
            log.debug("Event publishing disabled; not recording {} for {}", eventType, aggregateId);
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            // A serialization failure here is a programming error, not a runtime
            // condition we can recover from. Fail loudly so it surfaces in tests.
            throw new IllegalStateException("Could not serialize outbox event " + eventType, ex);
        }

        repository.save(new OutboxEvent(aggregateType, aggregateId, eventType, topic, messageKey, json));
        log.debug("Recorded outbox event {} for {} -> topic {}", eventType, aggregateId, topic);
    }
}
