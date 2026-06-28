package com.aurora.backend.messaging.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the outbox purge (OWASP A07 residual + table-bloat hygiene) deletes only PUBLISHED
 * rows older than the retention window, leaving recent PUBLISHED rows and PENDING rows intact.
 * Bloated relay/purge schedules are pushed far out so they don't interfere. Requires Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = {
        "app.outbox.relay.fixed-delay-ms=3600000",
        "app.outbox.purge.fixed-delay-ms=3600000",
        "app.outbox.purge.retention-minutes=60"
})
class OutboxPurgeTest {

    @Autowired private OutboxRelay outboxRelay;
    @Autowired private OutboxEventRepository repository;

    @Test
    void purgeRemovesOnlyPublishedRowsOlderThanRetention() {
        UUID oldPublished = save(publishedAt(Instant.now().minus(Duration.ofHours(2))));   // stale
        UUID recentPublished = save(publishedAt(Instant.now()));                            // just delivered
        UUID pending = save(new OutboxEvent("USER", "u3", "X", "t", "k", "{}"));            // not delivered

        outboxRelay.purgePublished();

        assertThat(repository.findById(oldPublished)).isEmpty();          // purged
        assertThat(repository.findById(recentPublished)).isPresent();     // too recent to purge
        assertThat(repository.findById(pending)).isPresent();             // never purged (PENDING)
    }

    private OutboxEvent publishedAt(Instant when) {
        OutboxEvent event = new OutboxEvent("USER", UUID.randomUUID().toString(), "X", "t", "k", "{}");
        event.markPublished();
        ReflectionTestUtils.setField(event, "publishedAt", when);
        return event;
    }

    private UUID save(OutboxEvent event) {
        return repository.saveAndFlush(event).getId();
    }
}
