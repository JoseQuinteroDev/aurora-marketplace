package com.aurora.backend.messaging.outbox;

import java.util.List;

import com.aurora.backend.messaging.DomainEventPublisher;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final DomainEventPublisher publisher = mock(DomainEventPublisher.class);

    private OutboxRelay relayWith(int maxAttempts) {
        Environment env = mock(Environment.class);
        when(env.getProperty("app.outbox.relay.batch-size", Integer.class, 100)).thenReturn(100);
        when(env.getProperty("app.outbox.relay.max-attempts", Integer.class, 10)).thenReturn(maxAttempts);
        when(env.getProperty("app.outbox.purge.enabled", Boolean.class, true)).thenReturn(true);
        when(env.getProperty("app.outbox.purge.retention-minutes", Integer.class, 60)).thenReturn(60);
        return new OutboxRelay(repository, publisher, env);
    }

    private OutboxEvent pendingEvent() {
        return new OutboxEvent("ORDER", "AUR-1", "ORDER_CREATED",
                "aurora.orders.created", "AUR-1", "{}");
    }

    @Test
    void marksEventPublishedWhenBrokerAcknowledges() {
        OutboxEvent event = pendingEvent();
        when(repository.claimBatch(eq(OutboxStatus.PENDING), any(Limit.class))).thenReturn(List.of(event));
        when(publisher.publish(any(), any(), any())).thenReturn(true);

        relayWith(10).drain();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void keepsEventPendingForRetryWhenPublishFails() {
        OutboxEvent event = pendingEvent();
        when(repository.claimBatch(eq(OutboxStatus.PENDING), any(Limit.class))).thenReturn(List.of(event));
        when(publisher.publish(any(), any(), any())).thenReturn(false);

        relayWith(10).drain();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isNotNull();
    }

    @Test
    void parksEventAsFailedOnceRetryBudgetIsExhausted() {
        OutboxEvent event = pendingEvent();
        when(repository.claimBatch(eq(OutboxStatus.PENDING), any(Limit.class))).thenReturn(List.of(event));
        when(publisher.publish(any(), any(), any())).thenReturn(false);

        OutboxRelay relay = relayWith(2);
        relay.drain(); // attempt 1 -> still PENDING
        relay.drain(); // attempt 2 -> FAILED

        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void doesNothingWhenThereAreNoPendingEvents() {
        when(repository.claimBatch(eq(OutboxStatus.PENDING), any(Limit.class))).thenReturn(List.of());

        relayWith(10).drain();

        verify(publisher, never()).publish(any(), any(), any());
    }
}
