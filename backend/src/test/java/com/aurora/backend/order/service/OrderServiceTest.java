package com.aurora.backend.order.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.common.exception.NotFoundException;
import com.aurora.backend.order.dto.OrderResponse;
import com.aurora.backend.order.dto.UpdateOrderStatusRequest;
import com.aurora.backend.order.entity.Order;
import com.aurora.backend.order.entity.OrderStatus;
import com.aurora.backend.order.repository.OrderRepository;
import com.aurora.backend.user.entity.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security unit tests for {@link OrderService}, focused on the ownership control
 * behind {@code GET /api/orders/{id}} (OWASP A01 — IDOR).
 *
 * <p>The headline assertion is that a customer can only read their <b>own</b> order:
 * {@code getUserOrder} resolves via the owner-scoped
 * {@link OrderRepository#findByIdAndUserId} and never via the unscoped
 * {@code findById}. This is the exact control that vulnerable-lab's {@code lab/01}
 * removes; this test locks it so the IDOR cannot silently return.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ATTACKER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private OrderService orderService;

    private User userWithId(UUID id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    private Order orderOwnedBy(User owner) {
        return new Order(
                "AUR-TEST-0001",
                owner,
                OrderStatus.PENDING_PAYMENT,
                null,
                new BigDecimal("100.00"),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("100.00")
        );
    }

    @Test
    void getUserOrderReturnsTheOrderWhenTheCallerOwnsIt() {
        User owner = userWithId(OWNER_ID);
        when(orderRepository.findByIdAndUserId(ORDER_ID, OWNER_ID))
                .thenReturn(Optional.of(orderOwnedBy(owner)));

        OrderResponse response = orderService.getUserOrder(owner, ORDER_ID);

        assertThat(response.orderNumber()).isEqualTo("AUR-TEST-0001");
        // The lookup MUST be owner-scoped, never the unscoped findById.
        verify(orderRepository).findByIdAndUserId(ORDER_ID, OWNER_ID);
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void getUserOrderHidesAnotherCustomersOrderAsNotFound() {
        // The attacker knows (or guesses) the victim's order id, but is a different user.
        User attacker = userWithId(ATTACKER_ID);
        // The owner-scoped query finds nothing for the attacker → empty.
        when(orderRepository.findByIdAndUserId(ORDER_ID, ATTACKER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getUserOrder(attacker, ORDER_ID))
                .isInstanceOf(NotFoundException.class);

        // It never falls back to an unscoped lookup that would leak the data.
        verify(orderRepository).findByIdAndUserId(ORDER_ID, ATTACKER_ID);
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void updateStatusChangesTheOrderStatusAndAuditsTheChange() {
        // updateStatus resolves by id (admin path); the user ids are not read here.
        Order order = orderOwnedBy(mock(User.class));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        OrderResponse response = orderService.updateStatus(
                ORDER_ID, new UpdateOrderStatusRequest(OrderStatus.PAID, "Marked paid"), mock(User.class));

        assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).saveAndFlush(order);
        verify(auditLogService).log(any(), any(), any(), any(), any());   // the change is audited
    }

    @Test
    void updateStatusOnAnUnknownOrderIsNotFound() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateStatus(
                ORDER_ID, new UpdateOrderStatusRequest(OrderStatus.PAID, null), mock(User.class)))
                .isInstanceOf(NotFoundException.class);

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void listUserOrdersReturnsOnlyTheCallersOrders() {
        User owner = userWithId(OWNER_ID);
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(OWNER_ID))
                .thenReturn(List.of(orderOwnedBy(owner)));

        assertThat(orderService.listUserOrders(owner)).hasSize(1);
        verify(orderRepository).findByUserIdOrderByCreatedAtDesc(OWNER_ID);
    }

    @Test
    void listAllOrdersMapsEveryOrderForAdmins() {
        when(orderRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(orderOwnedBy(mock(User.class)), orderOwnedBy(mock(User.class))));

        assertThat(orderService.listAllOrders()).hasSize(2);
    }
}
