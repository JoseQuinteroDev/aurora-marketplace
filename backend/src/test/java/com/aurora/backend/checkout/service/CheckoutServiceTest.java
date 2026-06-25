package com.aurora.backend.checkout.service;

import java.math.BigDecimal;
import java.util.Optional;

import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.cart.entity.Cart;
import com.aurora.backend.cart.entity.CartItem;
import com.aurora.backend.cart.repository.CartRepository;
import com.aurora.backend.catalog.product.entity.Product;
import com.aurora.backend.catalog.product.entity.ProductVariant;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.inventory.entity.Inventory;
import com.aurora.backend.inventory.repository.InventoryRepository;
import com.aurora.backend.inventory.repository.StockMovementRepository;
import com.aurora.backend.messaging.outbox.OutboxEventRecorder;
import com.aurora.backend.order.dto.OrderResponse;
import com.aurora.backend.order.repository.OrderRepository;
import com.aurora.backend.payment.entity.Payment;
import com.aurora.backend.payment.repository.PaymentRepository;
import com.aurora.backend.promotion.repository.CouponUsageRepository;
import com.aurora.backend.promotion.service.CouponService;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.role.Role;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security unit tests for {@link CheckoutService} — the most security-critical
 * service in the app, because it owns the money (OWASP A04).
 *
 * <p>The headline guarantee is that every monetary value on the order and the
 * payment is <b>recomputed server-side</b> from the cart and the catalog price:
 * the client never supplies, and can never influence, a price or total. This is
 * exactly the control that vulnerable-lab's {@code lab/03} removes (by trusting a
 * client {@code clientTotal}); this test locks it so "buy for $0" cannot return.
 */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    private static final BigDecimal UNIT_PRICE = new BigDecimal("1299.00");

    @Mock private CartRepository cartRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private CouponService couponService;
    @Mock private CouponUsageRepository couponUsageRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private OutboxEventRecorder outboxRecorder;

    @InjectMocks
    private CheckoutService checkoutService;

    private User customer() {
        return new User("buyer@aurora.test", "hash", "Buy", "Er", Role.CUSTOMER, true);
    }

    private ProductVariant variant(boolean active) {
        // priceOverride = null on purpose, so effectivePrice falls back to the
        // catalog base price — the server-owned source of truth for money.
        ProductVariant variant = new ProductVariant("SKU-1", "Default", null, null, active);
        Product product = new Product(
                "Desk Lamp", "desk-lamp", null, null, UNIT_PRICE, true, false, null, null);
        product.addVariant(variant);
        return variant;
    }

    private Cart cartWith(User user, ProductVariant variant, int quantity) {
        Cart cart = new Cart(user);
        cart.addItem(new CartItem(variant, quantity));
        return cart;
    }

    @Test
    void totalIsRecomputedFromCatalogPriceAndDrivesTheOrderAndPayment() {
        User user = customer();
        ProductVariant variant = variant(true);
        Cart cart = cartWith(user, variant, 2);          // 2 × 1299.00 = 2598.00
        Inventory inventory = new Inventory(variant, 10, 0, 2);

        when(cartRepository.findByUserId(any())).thenReturn(Optional.of(cart));
        when(inventoryRepository.findByVariantId(any())).thenReturn(Optional.of(inventory));
        when(inventoryRepository.findByVariantIdForUpdate(any())).thenReturn(Optional.of(inventory));
        when(couponService.calculateDiscount(any(), any(), any()))
                .thenReturn(BigDecimal.ZERO.setScale(2));
        when(orderRepository.existsByOrderNumber(any())).thenReturn(false);
        when(orderRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        OrderResponse order = checkoutService.confirmCheckout(user);

        // Order money is the server computation, full stop.
        assertThat(order.subtotal()).isEqualByComparingTo("2598.00");
        assertThat(order.discountTotal()).isEqualByComparingTo("0.00");
        assertThat(order.total()).isEqualByComparingTo("2598.00");
        assertThat(order.items()).singleElement()
                .satisfies(item -> assertThat(item.unitPrice()).isEqualByComparingTo(UNIT_PRICE));

        // The payment amount equals the recomputed total — never a client value.
        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertThat(payment.getValue().getAmount()).isEqualByComparingTo("2598.00");
    }

    @Test
    void emptyCartIsRejected() {
        User user = customer();
        when(cartRepository.findByUserId(any())).thenReturn(Optional.of(new Cart(user)));

        assertThatThrownBy(() -> checkoutService.confirmCheckout(user))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("CART_EMPTY"));

        verify(orderRepository, never()).saveAndFlush(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void inactiveVariantCannotBePurchased() {
        User user = customer();
        Cart cart = cartWith(user, variant(false), 1);
        when(cartRepository.findByUserId(any())).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> checkoutService.confirmCheckout(user))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("PRODUCT_VARIANT_INACTIVE"));

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void insufficientStockBlocksTheOrder() {
        User user = customer();
        ProductVariant variant = variant(true);
        Cart cart = cartWith(user, variant, 5);
        Inventory empty = new Inventory(variant, 0, 0, 2);     // nothing available
        when(cartRepository.findByUserId(any())).thenReturn(Optional.of(cart));
        when(inventoryRepository.findByVariantId(any())).thenReturn(Optional.of(empty));

        assertThatThrownBy(() -> checkoutService.confirmCheckout(user))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INSUFFICIENT_STOCK"));

        verify(orderRepository, never()).saveAndFlush(any());
    }
}
