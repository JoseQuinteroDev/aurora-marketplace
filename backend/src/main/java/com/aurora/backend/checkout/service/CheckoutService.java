package com.aurora.backend.checkout.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.cart.entity.Cart;
import com.aurora.backend.cart.entity.CartItem;
import com.aurora.backend.cart.repository.CartRepository;
import com.aurora.backend.catalog.product.entity.Product;
import com.aurora.backend.catalog.product.entity.ProductVariant;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.inventory.entity.Inventory;
import com.aurora.backend.inventory.entity.StockMovement;
import com.aurora.backend.inventory.entity.StockMovementType;
import com.aurora.backend.inventory.repository.InventoryRepository;
import com.aurora.backend.inventory.repository.StockMovementRepository;
import com.aurora.backend.messaging.AuroraTopics;
import com.aurora.backend.messaging.DomainEventPublisher;
import com.aurora.backend.messaging.event.OrderCreatedEvent;
import com.aurora.backend.order.dto.OrderResponse;
import com.aurora.backend.order.entity.Order;
import com.aurora.backend.order.entity.OrderItem;
import com.aurora.backend.order.entity.OrderStatus;
import com.aurora.backend.order.entity.OrderStatusHistory;
import com.aurora.backend.order.repository.OrderRepository;
import com.aurora.backend.payment.entity.Payment;
import com.aurora.backend.payment.entity.PaymentMethod;
import com.aurora.backend.payment.entity.PaymentStatus;
import com.aurora.backend.payment.repository.PaymentRepository;
import com.aurora.backend.promotion.entity.Coupon;
import com.aurora.backend.promotion.entity.CouponUsage;
import com.aurora.backend.promotion.repository.CouponUsageRepository;
import com.aurora.backend.promotion.service.CouponService;
import com.aurora.backend.user.entity.User;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutService {

    private final CartRepository cartRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CouponService couponService;
    private final CouponUsageRepository couponUsageRepository;
    private final AuditLogService auditLogService;
    private final DomainEventPublisher eventPublisher;

    private static final String CURRENCY = "USD";

    public CheckoutService(
            CartRepository cartRepository,
            InventoryRepository inventoryRepository,
            StockMovementRepository stockMovementRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            CouponService couponService,
            CouponUsageRepository couponUsageRepository,
            AuditLogService auditLogService,
            DomainEventPublisher eventPublisher
    ) {
        this.cartRepository = cartRepository;
        this.inventoryRepository = inventoryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.couponService = couponService;
        this.couponUsageRepository = couponUsageRepository;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse confirmCheckout(User user) {
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "CART_EMPTY",
                        "Cart is empty."
                ));

        if (cart.getItems().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CART_EMPTY", "Cart is empty.");
        }

        validateCartItems(cart);
        BigDecimal subtotal = calculateSubtotal(cart);
        Coupon coupon = cart.getCoupon() == null
                ? null
                : couponService.validateCouponForCart(cart.getCoupon().getCode(), user, subtotal);
        BigDecimal discountTotal = couponService.calculateDiscount(coupon, user, subtotal);
        BigDecimal total = subtotal.subtract(discountTotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        Order order = new Order(
                generateOrderNumber(),
                user,
                OrderStatus.PENDING_PAYMENT,
                coupon == null ? null : coupon.getCode(),
                subtotal,
                discountTotal,
                total
        );

        cart.getItems().forEach(item -> {
            ProductVariant variant = item.getVariant();
            Product product = variant.getProduct();
            BigDecimal unitPrice = effectivePrice(variant, product);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            order.addItem(new OrderItem(
                    product.getId(),
                    variant.getId(),
                    product.getName(),
                    product.getSlug(),
                    variant.getSku(),
                    variant.getName(),
                    item.getQuantity(),
                    unitPrice,
                    lineTotal
            ));

            decreaseStock(variant, item.getQuantity(), order.getOrderNumber());
        });

        order.addStatusHistory(new OrderStatusHistory(
                OrderStatus.PENDING_PAYMENT,
                "Order created from checkout.",
                user
        ));

        Order savedOrder = orderRepository.saveAndFlush(order);
        paymentRepository.save(new Payment(savedOrder, PaymentStatus.PENDING, PaymentMethod.SIMULATED_CARD, total));

        if (coupon != null) {
            couponUsageRepository.save(new CouponUsage(coupon, user));
            auditLogService.log(
                    AuditEventType.COUPON_USED,
                    user,
                    "COUPON",
                    coupon.getId(),
                    "Coupon " + coupon.getCode() + " used for order " + savedOrder.getOrderNumber()
            );
        }

        auditLogService.log(
                AuditEventType.ORDER_CREATED,
                user,
                "ORDER",
                savedOrder.getId(),
                "Order " + savedOrder.getOrderNumber() + " created."
        );

        cart.clearItems();

        eventPublisher.publish(
                AuroraTopics.ORDER_CREATED,
                savedOrder.getOrderNumber(),
                OrderCreatedEvent.of(
                        savedOrder.getId(),
                        savedOrder.getOrderNumber(),
                        user.getEmail(),
                        user.getFirstName() + " " + user.getLastName(),
                        savedOrder.getItems().size(),
                        savedOrder.getSubtotal(),
                        savedOrder.getDiscountTotal(),
                        savedOrder.getTotal(),
                        CURRENCY,
                        savedOrder.getStatus().name()
                )
        );

        return OrderResponse.from(savedOrder);
    }

    private void validateCartItems(Cart cart) {
        for (CartItem item : cart.getItems()) {
            ProductVariant variant = item.getVariant();
            Product product = variant.getProduct();

            if (!variant.isActive() || !product.isActive()) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "PRODUCT_VARIANT_INACTIVE",
                        "Product variant is not active."
                );
            }

            ensureStockAvailable(variant, item.getQuantity());
        }
    }

    private void decreaseStock(ProductVariant variant, int quantity, String orderNumber) {
        Inventory inventory = getInventory(variant);
        inventory.adjustAvailableQuantity(inventory.getAvailableQuantity() - quantity);
        stockMovementRepository.save(new StockMovement(
                variant,
                StockMovementType.SALE,
                quantity,
                "Checkout order " + orderNumber
        ));
    }

    private void ensureStockAvailable(ProductVariant variant, int quantity) {
        Inventory inventory = getInventory(variant);

        if (inventory.getAvailableQuantity() < quantity) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_STOCK",
                    "Not enough available stock for variant " + variant.getSku() + "."
            );
        }
    }

    private Inventory getInventory(ProductVariant variant) {
        return inventoryRepository.findByVariantId(variant.getId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.CONFLICT,
                        "INVENTORY_NOT_AVAILABLE",
                        "Inventory is not available for variant " + variant.getSku() + "."
                ));
    }

    private BigDecimal calculateSubtotal(Cart cart) {
        return cart.getItems().stream()
                .map(item -> effectivePrice(item.getVariant(), item.getVariant().getProduct())
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal effectivePrice(ProductVariant variant, Product product) {
        BigDecimal price = variant.getPriceOverride() == null ? product.getBasePrice() : variant.getPriceOverride();
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private String generateOrderNumber() {
        String orderNumber;

        do {
            orderNumber = "AUR-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (orderRepository.existsByOrderNumber(orderNumber));

        return orderNumber;
    }
}
