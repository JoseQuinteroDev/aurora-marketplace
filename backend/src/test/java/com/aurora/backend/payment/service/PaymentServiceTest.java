package com.aurora.backend.payment.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;
import com.aurora.backend.messaging.outbox.OutboxEventRecorder;
import com.aurora.backend.order.entity.Order;
import com.aurora.backend.order.entity.OrderStatus;
import com.aurora.backend.order.repository.OrderRepository;
import com.aurora.backend.payment.dto.PaymentResponse;
import com.aurora.backend.payment.dto.PaymentSimulationRequest;
import com.aurora.backend.payment.repository.PaymentRepository;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.role.Role;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security/integrity unit tests for {@link PaymentService} — the payment half of
 * the commerce core. Asserts: the order is fetched owner-scoped (A01/IDOR), the
 * charged amount is the server-side order total (never client-supplied — the
 * request carries no amount, A04), terminal states are protected, and the right
 * domain event is recorded for success vs. failure.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final BigDecimal TOTAL = new BigDecimal("100.00");

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private OutboxEventRecorder outboxRecorder;

    @InjectMocks
    private PaymentService paymentService;

    private User customer() {
        return new User("pay@aurora.test", "hash", "Pay", "Er", Role.CUSTOMER, true);
    }

    private Order order(OrderStatus status) {
        return new Order("AUR-PAY-1", customer(), status, null, TOTAL, BigDecimal.ZERO.setScale(2), TOTAL);
    }

    private void orderIsFound(Order order) {
        when(orderRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(order));
    }

    private void newPaymentRow() {
        when(paymentRepository.findByOrderId(any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(c -> c.getArgument(0));
        when(paymentRepository.saveAndFlush(any())).thenAnswer(c -> c.getArgument(0));
    }

    @Test
    void payingAnotherCustomersOrderIsNotFound() {
        when(orderRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.simulatePayment(customer(), ORDER_ID, new PaymentSimulationRequest(true, "ok")))
                .isInstanceOf(NotFoundException.class);

        verify(outboxRecorder, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void successfulPaymentMarksTheOrderPaidAndEmitsConfirmedWithTheServerTotal() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        orderIsFound(order);
        newPaymentRow();

        PaymentResponse response = paymentService.simulatePayment(customer(), ORDER_ID, new PaymentSimulationRequest(true, null));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(response.amount()).isEqualByComparingTo("100.00");   // server total, not a client value
        verify(outboxRecorder).record(eq("ORDER"), any(), eq("PAYMENT_CONFIRMED"), any(), any(), any());
    }

    @Test
    void failedPaymentKeepsOrderPendingAndEmitsFailed() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        orderIsFound(order);
        newPaymentRow();

        paymentService.simulatePayment(customer(), ORDER_ID, new PaymentSimulationRequest(false, "Card declined"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(outboxRecorder).record(eq("ORDER"), any(), eq("PAYMENT_FAILED"), any(), any(), any());
    }

    @Test
    void payingAnAlreadyPaidOrderIsRejected() {
        orderIsFound(order(OrderStatus.PAID));

        assertThatThrownBy(() -> paymentService.simulatePayment(customer(), ORDER_ID, new PaymentSimulationRequest(true, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ORDER_ALREADY_PAID"));

        verify(outboxRecorder, never()).record(any(), any(), any(), any(), any(), any());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void payingACancelledOrderIsRejected() {
        orderIsFound(order(OrderStatus.CANCELLED));

        assertThatThrownBy(() -> paymentService.simulatePayment(customer(), ORDER_ID, new PaymentSimulationRequest(true, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ORDER_NOT_PAYABLE"));

        verify(outboxRecorder, never()).record(any(), any(), any(), any(), any(), any());
    }
}
