package com.aurora.backend.messaging.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.aurora.backend.messaging.DomainEventPublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the {@link OutboxEvent outbox} to Kafka.
 *
 * <p>On a fixed schedule it claims a batch of PENDING rows, publishes each to
 * the broker and marks it PUBLISHED. A row that fails to publish keeps its
 * PENDING status (so the next tick retries it) until it exhausts the retry
 * budget, after which it is parked as FAILED for inspection. The whole tick
 * runs in one transaction, and the {@code SKIP LOCKED} claim query lets several
 * core instances relay concurrently without sending duplicates.</p>
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final DomainEventPublisher publisher;
    private final int batchSize;
    private final int maxAttempts;
    private final boolean purgeEnabled;
    private final int purgeRetentionMinutes;

    public OutboxRelay(
            OutboxEventRepository repository,
            DomainEventPublisher publisher,
            org.springframework.core.env.Environment env
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.batchSize = env.getProperty("app.outbox.relay.batch-size", Integer.class, 100);
        this.maxAttempts = env.getProperty("app.outbox.relay.max-attempts", Integer.class, 10);
        this.purgeEnabled = env.getProperty("app.outbox.purge.enabled", Boolean.class, true);
        this.purgeRetentionMinutes = env.getProperty("app.outbox.purge.retention-minutes", Integer.class, 60);
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay.fixed-delay-ms:2000}")
    @Transactional
    public void drain() {
        List<OutboxEvent> batch = repository.claimBatch(OutboxStatus.PENDING, Limit.of(batchSize));
        if (batch.isEmpty()) {
            return;
        }

        int published = 0;
        int failed = 0;
        for (OutboxEvent event : batch) {
            boolean sent = publisher.publish(event.getTopic(), event.getMessageKey(), event.getPayload());
            if (sent) {
                event.markPublished();
                published++;
            } else {
                event.markAttemptFailed("Broker did not acknowledge the record", maxAttempts);
                failed++;
                if (event.getStatus() == OutboxStatus.FAILED) {
                    log.error("Outbox event {} ({}) parked as FAILED after {} attempts",
                            event.getId(), event.getEventType(), event.getAttempts());
                }
            }
        }

        log.debug("Outbox relay tick: {} published, {} deferred/failed", published, failed);
    }

    /**
     * Periodically deletes PUBLISHED rows older than the retention window. Two wins: it keeps the
     * outbox table from growing unbounded, and it bounds how long an event payload's sensitive
     * cleartext (a password-reset / email-verification token) survives in the DB after delivery
     * (OWASP A07 residual). PENDING/FAILED rows are never touched — only successfully relayed ones.
     */
    @Scheduled(fixedDelayString = "${app.outbox.purge.fixed-delay-ms:900000}")
    @Transactional
    public void purgePublished() {
        if (!purgeEnabled) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(purgeRetentionMinutes));
        int deleted = repository.deleteByStatusAndPublishedAtBefore(OutboxStatus.PUBLISHED, cutoff);
        if (deleted > 0) {
            log.info("Outbox purge: removed {} PUBLISHED rows older than {} min.", deleted, purgeRetentionMinutes);
        }
    }
}
