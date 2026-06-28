package com.aurora.backend.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRODUCER half of the Kafka JSON contract (see also the matching
 * {@code com.aurora.notification.event.EventContractTest} on the consumer side).
 *
 * <p>There is NO shared event library between the core and the notification-service —
 * the wire contract IS the JSON shape the producer emits. This test pins that shape:
 * for every producer event record it serializes an instance with an {@link ObjectMapper}
 * configured the way the running core configures it (Spring Boot auto-config registers
 * {@code JavaTimeModule} and disables {@code WRITE_DATES_AS_TIMESTAMPS}, so {@code Instant}
 * serializes as an ISO-8601 string), and asserts the EXACT field names plus the wire form
 * of {@code Instant} / {@code BigDecimal} / {@code UUID}. A field rename, retype, or a
 * change in date handling therefore breaks this build instead of silently breaking
 * downstream consumers at runtime.</p>
 *
 * <p>LOCKSTEP: this file and the consumer-side {@code EventContractTest} are two halves of
 * one contract. If a producer field here changes, the consumer test (and consumer record)
 * MUST change with it, and vice-versa — they must stay in lockstep.</p>
 */
class EventContractTest {

    /**
     * Mirrors the {@code ObjectMapper} the core actually serializes outbox events with.
     * {@code OutboxEventRecorder} injects the Spring-managed bean, which Spring Boot builds
     * via {@code Jackson2ObjectMapperBuilder}: {@code JavaTimeModule} registered and
     * {@code WRITE_DATES_AS_TIMESTAMPS} disabled (→ Instants as ISO-8601 strings, not epoch
     * numbers). Replicating it here keeps the assertions about the real production wire form.
     */
    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private static final Instant FIXED = Instant.parse("2026-06-28T10:15:30.123456789Z");
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private JsonNode serialize(Object event) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(event));
    }

    @Test
    void orderCreatedEventHasTheExpectedWireShape() throws Exception {
        OrderCreatedEvent event = new OrderCreatedEvent(
                "evt-1", FIXED, ID, "AUR-1001",
                "ada@aurora.test", "Ada Lovelace", "+34123456789",
                2, new BigDecimal("99.90"), new BigDecimal("10.00"),
                new BigDecimal("89.90"), "USD", "CREATED");

        JsonNode json = serialize(event);

        // Exact field name set — a rename/add/drop fails here.
        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "eventId", "occurredAt", "orderId", "orderNumber",
                "customerEmail", "customerName", "customerPhone", "itemCount",
                "subtotal", "discountTotal", "total", "currency", "status");

        assertThat(json.get("eventId").asText()).isEqualTo("evt-1");
        // Instant: ISO-8601 STRING (not an epoch number), round-trippable.
        assertThat(json.get("occurredAt").isTextual()).isTrue();
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-06-28T10:15:30.123456789Z");
        // UUID: lowercase dashed STRING.
        assertThat(json.get("orderId").isTextual()).isTrue();
        assertThat(json.get("orderId").asText()).isEqualTo("11111111-2222-3333-4444-555555555555");
        assertThat(json.get("orderNumber").asText()).isEqualTo("AUR-1001");
        assertThat(json.get("customerEmail").asText()).isEqualTo("ada@aurora.test");
        assertThat(json.get("customerName").asText()).isEqualTo("Ada Lovelace");
        assertThat(json.get("customerPhone").asText()).isEqualTo("+34123456789");
        // int: JSON number.
        assertThat(json.get("itemCount").isInt()).isTrue();
        assertThat(json.get("itemCount").asInt()).isEqualTo(2);
        // BigDecimal: serialized as a JSON number. NOTE the wire form does NOT preserve a
        // trailing-zero scale (99.90 -> 99.9), so consumers must compare money by value, not scale.
        assertThat(json.get("subtotal").isNumber()).isTrue();
        assertThat(json.get("subtotal").decimalValue()).isEqualByComparingTo("99.90");
        assertThat(json.get("subtotal").asText()).isEqualTo("99.9");
        assertThat(json.get("discountTotal").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(json.get("total").decimalValue()).isEqualByComparingTo("89.90");
        assertThat(json.get("currency").asText()).isEqualTo("USD");
        assertThat(json.get("status").asText()).isEqualTo("CREATED");
    }

    @Test
    void paymentConfirmedEventHasTheExpectedWireShape() throws Exception {
        PaymentConfirmedEvent event = new PaymentConfirmedEvent(
                "pc-1", FIXED, ID, "AUR-1001",
                "ada@aurora.test", "Ada Lovelace",
                new BigDecimal("89.90"), "USD", "CARD");

        JsonNode json = serialize(event);

        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "eventId", "occurredAt", "orderId", "orderNumber",
                "customerEmail", "customerName", "amount", "currency", "paymentMethod");

        assertThat(json.get("eventId").asText()).isEqualTo("pc-1");
        assertThat(json.get("occurredAt").isTextual()).isTrue();
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-06-28T10:15:30.123456789Z");
        assertThat(json.get("orderId").asText()).isEqualTo("11111111-2222-3333-4444-555555555555");
        assertThat(json.get("orderNumber").asText()).isEqualTo("AUR-1001");
        // BigDecimal money: JSON number; trailing-zero scale is not preserved on the wire (89.90 -> 89.9).
        assertThat(json.get("amount").isNumber()).isTrue();
        assertThat(json.get("amount").decimalValue()).isEqualByComparingTo("89.90");
        assertThat(json.get("amount").asText()).isEqualTo("89.9");
        assertThat(json.get("currency").asText()).isEqualTo("USD");
        assertThat(json.get("paymentMethod").asText()).isEqualTo("CARD");
    }

    @Test
    void paymentFailedEventHasTheExpectedWireShape() throws Exception {
        PaymentFailedEvent event = new PaymentFailedEvent(
                "pf-1", FIXED, ID, "AUR-1001",
                "ada@aurora.test", "Ada Lovelace",
                new BigDecimal("89.90"), "USD", "Card declined");

        JsonNode json = serialize(event);

        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "eventId", "occurredAt", "orderId", "orderNumber",
                "customerEmail", "customerName", "amount", "currency", "reason");

        assertThat(json.get("eventId").asText()).isEqualTo("pf-1");
        assertThat(json.get("occurredAt").isTextual()).isTrue();
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-06-28T10:15:30.123456789Z");
        assertThat(json.get("orderId").asText()).isEqualTo("11111111-2222-3333-4444-555555555555");
        assertThat(json.get("amount").decimalValue()).isEqualByComparingTo("89.90");
        assertThat(json.get("currency").asText()).isEqualTo("USD");
        assertThat(json.get("reason").asText()).isEqualTo("Card declined");
    }

    @Test
    void passwordResetRequestedEventHasTheExpectedWireShape() throws Exception {
        PasswordResetRequestedEvent event = new PasswordResetRequestedEvent(
                "pr-1", FIXED, ID, "ada@aurora.test", "Ada Lovelace",
                "rid.secret", 30);

        JsonNode json = serialize(event);

        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "eventId", "occurredAt", "userId",
                "customerEmail", "customerName", "resetToken", "expiresInMinutes");

        assertThat(json.get("eventId").asText()).isEqualTo("pr-1");
        assertThat(json.get("occurredAt").isTextual()).isTrue();
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-06-28T10:15:30.123456789Z");
        // userId is the UUID component here (not orderId).
        assertThat(json.get("userId").isTextual()).isTrue();
        assertThat(json.get("userId").asText()).isEqualTo("11111111-2222-3333-4444-555555555555");
        assertThat(json.get("customerEmail").asText()).isEqualTo("ada@aurora.test");
        assertThat(json.get("resetToken").asText()).isEqualTo("rid.secret");
        assertThat(json.get("expiresInMinutes").isInt()).isTrue();
        assertThat(json.get("expiresInMinutes").asInt()).isEqualTo(30);
    }

    @Test
    void emailVerificationRequestedEventHasTheExpectedWireShape() throws Exception {
        EmailVerificationRequestedEvent event = new EmailVerificationRequestedEvent(
                "ev-1", FIXED, ID, "new@aurora.test", "Newbie",
                "ev.secret", 1440);

        JsonNode json = serialize(event);

        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "eventId", "occurredAt", "userId",
                "customerEmail", "customerName", "verificationToken", "expiresInMinutes");

        assertThat(json.get("eventId").asText()).isEqualTo("ev-1");
        assertThat(json.get("occurredAt").isTextual()).isTrue();
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-06-28T10:15:30.123456789Z");
        assertThat(json.get("userId").asText()).isEqualTo("11111111-2222-3333-4444-555555555555");
        assertThat(json.get("customerEmail").asText()).isEqualTo("new@aurora.test");
        assertThat(json.get("verificationToken").asText()).isEqualTo("ev.secret");
        assertThat(json.get("expiresInMinutes").isInt()).isTrue();
        assertThat(json.get("expiresInMinutes").asInt()).isEqualTo(1440);
    }

    private static java.util.List<String> fieldNames(JsonNode node) {
        java.util.List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
