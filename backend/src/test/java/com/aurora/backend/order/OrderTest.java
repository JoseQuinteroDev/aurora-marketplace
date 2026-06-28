package com.aurora.backend.order;

import java.math.BigDecimal;

import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.order.entity.Order;
import com.aurora.backend.order.entity.OrderStatus;
import com.aurora.backend.user.entity.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the order-status state machine in {@link Order#changeStatus}
 * (OWASP A04 — business-logic integrity). {@code changeStatus} is reachable from
 * the admin {@code PATCH /api/admin/orders/{id}/status} endpoint and the internal
 * payment flow; before this control it was a raw assignment, so an admin could
 * force illegal jumps (e.g. {@code DELIVERED -> PENDING_PAYMENT}).
 *
 * <p>These tests pin both directions: every transition the production code
 * legitimately performs still succeeds, and representative illegal moves throw
 * a {@code 409 ILLEGAL_ORDER_TRANSITION}. They fail if the guard is removed.
 */
class OrderTest {

    private Order orderInStatus(OrderStatus status) {
        return new Order(
                "AUR-TEST-0001",
                mock(User.class),
                status,
                null,
                new BigDecimal("100.00"),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("100.00")
        );
    }

    // --- Legal transitions: must keep working so we never break real flows. ---

    @ParameterizedTest
    @CsvSource({
            // Payment + checkout paths.
            "PENDING_PAYMENT, PAID",            // payment success (PaymentService)
            "CREATED, PENDING_PAYMENT",         // payment-failure regression path
            "CREATED, PAID",
            "CREATED, CANCELLED",
            "PENDING_PAYMENT, CANCELLED",
            // Admin fulfilment flow.
            "PAID, PREPARING",
            "PAID, SHIPPED",
            "PAID, CANCELLED",
            "PAID, REFUNDED",
            "PREPARING, SHIPPED",
            "PREPARING, CANCELLED",
            "PREPARING, REFUNDED",
            "SHIPPED, DELIVERED",
            "SHIPPED, REFUNDED",
            "DELIVERED, REFUNDED"
    })
    void legalTransitionsSucceed(OrderStatus from, OrderStatus to) {
        Order order = orderInStatus(from);

        order.changeStatus(to);

        assertThat(order.getStatus()).isEqualTo(to);
    }

    // --- Illegal transitions: backwards moves and skips to/from terminal states. ---

    @ParameterizedTest
    @CsvSource({
            // The headline exploit: rewinding a delivered order back to payment.
            "DELIVERED, PENDING_PAYMENT",
            "DELIVERED, PAID",
            "DELIVERED, SHIPPED",
            // Skipping the fulfilment chain.
            "PENDING_PAYMENT, SHIPPED",
            "PENDING_PAYMENT, DELIVERED",
            "PAID, DELIVERED",
            // Backwards from later stages.
            "SHIPPED, PAID",
            "SHIPPED, PREPARING",
            "PREPARING, PAID",
            "PAID, PENDING_PAYMENT",
            // Terminal states cannot transition anywhere.
            "CANCELLED, PAID",
            "CANCELLED, PENDING_PAYMENT",
            "REFUNDED, PAID",
            "REFUNDED, DELIVERED"
    })
    void illegalTransitionsAreRejected(OrderStatus from, OrderStatus to) {
        Order order = orderInStatus(from);

        assertThatThrownBy(() -> order.changeStatus(to))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getCode()).isEqualTo("ILLEGAL_ORDER_TRANSITION");
                });

        // The illegal move must not mutate the order's state.
        assertThat(order.getStatus()).isEqualTo(from);
    }

    @Test
    void illegalTransitionDoesNotChangeStatus() {
        Order order = orderInStatus(OrderStatus.DELIVERED);

        assertThatThrownBy(() -> order.changeStatus(OrderStatus.PENDING_PAYMENT))
                .isInstanceOf(BusinessException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void reapplyingTheCurrentStatusIsAnIdempotentNoOp() {
        // The payment-failure path and re-submitted admin updates rely on this:
        // setting the status to its current value is allowed even for terminal states.
        Order order = orderInStatus(OrderStatus.PENDING_PAYMENT);
        order.changeStatus(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        Order delivered = orderInStatus(OrderStatus.DELIVERED);
        delivered.changeStatus(OrderStatus.DELIVERED);
        assertThat(delivered.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }
}
