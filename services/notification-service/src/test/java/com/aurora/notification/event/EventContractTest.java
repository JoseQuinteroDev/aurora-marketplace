package com.aurora.notification.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CONSUMER half of the Kafka JSON contract (see also the matching
 * {@code com.aurora.backend.messaging.event.EventContractTest} on the producer side).
 *
 * <p>There is NO shared event library — the notification-service owns its own copies of the
 * event records and re-derives them from the producer's JSON. This test feeds each consumer
 * record the CANONICAL JSON the core actually emits (the exact shape pinned by the producer
 * test: ISO-8601 {@code occurredAt}, {@code BigDecimal} amounts carrying scale, dashed UUID
 * strings) and deserializes it with an {@link ObjectMapper} configured the way the running
 * notification-service configures it (Spring Boot auto-config: {@code JavaTimeModule}
 * registered). It then asserts every required component is non-null and correctly typed, so a
 * field rename/retype on the producer would surface here as a broken build, not a runtime DLT
 * flood.</p>
 *
 * <p>LOCKSTEP: this file and the producer-side {@code EventContractTest} are two halves of one
 * contract. The canonical JSON below MUST match the shape the producer test pins; if one side's
 * fields change, the other MUST change with it — they must stay in lockstep.</p>
 */
class EventContractTest {

    /**
     * Mirrors the {@code ObjectMapper} the notification-service deserializes with. The
     * {@code NotificationListener} injects the Spring-managed bean, which Spring Boot builds with
     * {@code JavaTimeModule} registered (so ISO-8601 {@code occurredAt} parses into an
     * {@code Instant}). A bare {@code new ObjectMapper()} would NOT parse the Instant — using the
     * production-equivalent mapper is the point of this contract test.
     */
    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .build();

    private static final String UUID_STR = "11111111-2222-3333-4444-555555555555";
    private static final String OCCURRED_AT = "2026-06-28T10:15:30.123456789Z";

    @Test
    void deserializesCanonicalOrderCreatedJson() throws Exception {
        String json = """
                {"eventId":"evt-1","occurredAt":"%s","orderId":"%s","orderNumber":"AUR-1001",
                 "customerEmail":"ada@aurora.test","customerName":"Ada Lovelace",
                 "customerPhone":"+34123456789","itemCount":2,
                 "subtotal":99.90,"discountTotal":10.00,"total":89.90,
                 "currency":"USD","status":"CREATED"}
                """.formatted(OCCURRED_AT, UUID_STR);

        OrderCreatedEvent event = mapper.readValue(json, OrderCreatedEvent.class);

        assertThat(event.eventId()).isEqualTo("evt-1");
        // Instant parsed from the ISO-8601 string the producer emits.
        assertThat(event.occurredAt()).isEqualTo(Instant.parse(OCCURRED_AT));
        // UUID parsed from the dashed string.
        assertThat(event.orderId()).isEqualTo(UUID.fromString(UUID_STR));
        assertThat(event.orderNumber()).isEqualTo("AUR-1001");
        assertThat(event.customerEmail()).isEqualTo("ada@aurora.test");
        assertThat(event.customerName()).isEqualTo("Ada Lovelace");
        assertThat(event.customerPhone()).isEqualTo("+34123456789");
        assertThat(event.itemCount()).isEqualTo(2);
        // BigDecimal scale preserved off the wire.
        assertThat(event.subtotal()).isEqualByComparingTo("99.90");
        assertThat(event.discountTotal()).isEqualByComparingTo("10.00");
        assertThat(event.total()).isEqualByComparingTo("89.90");
        assertThat(event.currency()).isEqualTo("USD");
        assertThat(event.status()).isEqualTo("CREATED");
    }

    @Test
    void deserializesCanonicalPaymentConfirmedJson() throws Exception {
        String json = """
                {"eventId":"pc-1","occurredAt":"%s","orderId":"%s","orderNumber":"AUR-1001",
                 "customerEmail":"ada@aurora.test","customerName":"Ada Lovelace",
                 "amount":89.90,"currency":"USD","paymentMethod":"CARD"}
                """.formatted(OCCURRED_AT, UUID_STR);

        PaymentConfirmedEvent event = mapper.readValue(json, PaymentConfirmedEvent.class);

        assertThat(event.eventId()).isEqualTo("pc-1");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse(OCCURRED_AT));
        assertThat(event.orderId()).isEqualTo(UUID.fromString(UUID_STR));
        assertThat(event.orderNumber()).isEqualTo("AUR-1001");
        assertThat(event.customerEmail()).isEqualTo("ada@aurora.test");
        assertThat(event.customerName()).isEqualTo("Ada Lovelace");
        assertThat(event.amount()).isEqualByComparingTo("89.90");
        assertThat(event.currency()).isEqualTo("USD");
        assertThat(event.paymentMethod()).isEqualTo("CARD");
    }

    @Test
    void deserializesCanonicalPaymentFailedJson() throws Exception {
        String json = """
                {"eventId":"pf-1","occurredAt":"%s","orderId":"%s","orderNumber":"AUR-1001",
                 "customerEmail":"ada@aurora.test","customerName":"Ada Lovelace",
                 "amount":89.90,"currency":"USD","reason":"Card declined"}
                """.formatted(OCCURRED_AT, UUID_STR);

        PaymentFailedEvent event = mapper.readValue(json, PaymentFailedEvent.class);

        assertThat(event.eventId()).isEqualTo("pf-1");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse(OCCURRED_AT));
        assertThat(event.orderId()).isEqualTo(UUID.fromString(UUID_STR));
        assertThat(event.orderNumber()).isEqualTo("AUR-1001");
        assertThat(event.customerEmail()).isEqualTo("ada@aurora.test");
        assertThat(event.customerName()).isEqualTo("Ada Lovelace");
        assertThat(event.amount()).isEqualByComparingTo("89.90");
        assertThat(event.currency()).isEqualTo("USD");
        assertThat(event.reason()).isEqualTo("Card declined");
    }

    @Test
    void deserializesCanonicalPasswordResetRequestedJson() throws Exception {
        String json = """
                {"eventId":"pr-1","occurredAt":"%s","userId":"%s",
                 "customerEmail":"ada@aurora.test","customerName":"Ada Lovelace",
                 "resetToken":"rid.secret","expiresInMinutes":30}
                """.formatted(OCCURRED_AT, UUID_STR);

        PasswordResetRequestedEvent event = mapper.readValue(json, PasswordResetRequestedEvent.class);

        assertThat(event.eventId()).isEqualTo("pr-1");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse(OCCURRED_AT));
        // userId is the UUID component here (not orderId).
        assertThat(event.userId()).isEqualTo(UUID.fromString(UUID_STR));
        assertThat(event.customerEmail()).isEqualTo("ada@aurora.test");
        assertThat(event.customerName()).isEqualTo("Ada Lovelace");
        assertThat(event.resetToken()).isEqualTo("rid.secret");
        assertThat(event.expiresInMinutes()).isEqualTo(30);
    }

    @Test
    void deserializesCanonicalEmailVerificationRequestedJson() throws Exception {
        String json = """
                {"eventId":"ev-1","occurredAt":"%s","userId":"%s",
                 "customerEmail":"new@aurora.test","customerName":"Newbie",
                 "verificationToken":"ev.secret","expiresInMinutes":1440}
                """.formatted(OCCURRED_AT, UUID_STR);

        EmailVerificationRequestedEvent event = mapper.readValue(json, EmailVerificationRequestedEvent.class);

        assertThat(event.eventId()).isEqualTo("ev-1");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse(OCCURRED_AT));
        assertThat(event.userId()).isEqualTo(UUID.fromString(UUID_STR));
        assertThat(event.customerEmail()).isEqualTo("new@aurora.test");
        assertThat(event.customerName()).isEqualTo("Newbie");
        assertThat(event.verificationToken()).isEqualTo("ev.secret");
        assertThat(event.expiresInMinutes()).isEqualTo(1440);
    }
}
