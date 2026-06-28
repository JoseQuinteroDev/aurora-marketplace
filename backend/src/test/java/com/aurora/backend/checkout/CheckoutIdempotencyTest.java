package com.aurora.backend.checkout;

import java.math.BigDecimal;
import java.util.UUID;

import com.aurora.backend.TestcontainersConfiguration;
import com.aurora.backend.checkout.entity.CheckoutIdempotencyKey;
import com.aurora.backend.checkout.repository.CheckoutIdempotencyKeyRepository;
import com.aurora.backend.checkout.service.CheckoutService;
import com.aurora.backend.order.dto.OrderResponse;
import com.aurora.backend.order.entity.Order;
import com.aurora.backend.order.entity.OrderStatus;
import com.aurora.backend.order.repository.OrderRepository;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;
import com.aurora.backend.user.role.Role;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves checkout idempotency (OWASP A04 — safe retries) end to end against PostgreSQL:
 * the {@code (user_id, idempotency_key)} unique constraint blocks a duplicate, and
 * {@link CheckoutService#confirmCheckout} replays the recorded order for a known key instead
 * of creating a second one. Requires Docker (Testcontainers).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CheckoutIdempotencyTest {

    @Autowired private CheckoutService checkoutService;
    @Autowired private CheckoutIdempotencyKeyRepository idempotencyRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void aSecondRowWithTheSameUserAndKeyViolatesTheUniqueConstraint() {
        User user = newUser();
        Order order = newOrder(user);
        String key = "dup-" + UUID.randomUUID();

        idempotencyRepository.saveAndFlush(new CheckoutIdempotencyKey(user, key, order));

        // The DB-level constraint is the backstop a truly-concurrent duplicate hits.
        assertThatThrownBy(() ->
                idempotencyRepository.saveAndFlush(new CheckoutIdempotencyKey(user, key, order)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void confirmCheckoutReplaysTheRecordedOrderForAKnownKeyWithoutCreatingANewOne() {
        User user = newUser();
        Order order = newOrder(user);
        String key = "replay-" + UUID.randomUUID();
        idempotencyRepository.saveAndFlush(new CheckoutIdempotencyKey(user, key, order));

        long ordersBefore = orderRepository.count();

        OrderResponse response = checkoutService.confirmCheckout(user, key);

        assertThat(response.orderNumber()).isEqualTo(order.getOrderNumber());
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);   // replay, not a new order
    }

    private User newUser() {
        return userRepository.save(
                new User("idem-" + UUID.randomUUID() + "@aurora.test", "hash", "Id", "Em", Role.CUSTOMER, true));
    }

    private Order newOrder(User user) {
        return orderRepository.save(new Order(
                "ORD-" + UUID.randomUUID(), user, OrderStatus.PENDING_PAYMENT, null,
                new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("10.00")));
    }
}
