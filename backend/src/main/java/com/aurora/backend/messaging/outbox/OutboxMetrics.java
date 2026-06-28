package com.aurora.backend.messaging.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

/**
 * Domain observability for the transactional outbox (OWASP A09 — make the architecture legible
 * on a dashboard). Publishes two gauges on the existing Prometheus registry:
 *
 * <ul>
 *   <li>{@code aurora.outbox.events{status="pending"}} — rows recorded in a business transaction
 *       but not yet relayed to Kafka. A sustained rise means the relay is falling behind or the
 *       broker is unreachable (checkout/payment still succeed; events just queue).</li>
 *   <li>{@code aurora.outbox.events{status="failed"}} — rows that exhausted the relay retry budget
 *       and were parked for inspection. Any non-zero value warrants an alert.</li>
 * </ul>
 *
 * <p>The gauge values are read lazily (per scrape) via a cheap {@code countByStatus} query, so
 * there is no cost unless Prometheus is scraping.</p>
 */
@Component
public class OutboxMetrics {

    private static final String METRIC = "aurora.outbox.events";

    public OutboxMetrics(OutboxEventRepository repository, MeterRegistry registry) {
        Gauge.builder(METRIC, repository, r -> r.countByStatus(OutboxStatus.PENDING))
                .description("Outbox rows recorded but not yet published to Kafka (relay backlog).")
                .tag("status", "pending")
                .register(registry);
        Gauge.builder(METRIC, repository, r -> r.countByStatus(OutboxStatus.FAILED))
                .description("Outbox rows that exhausted the relay retry budget (parked for inspection).")
                .tag("status", "failed")
                .register(registry);
    }
}
