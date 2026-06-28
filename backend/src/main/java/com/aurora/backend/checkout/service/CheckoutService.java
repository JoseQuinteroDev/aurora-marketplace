package com.aurora.backend.checkout.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.audit.entity.AuditEventType;
import com.aurora.backend.audit.service.AuditLogService;
import com.aurora.backend.cart.entity.Cart;
import com.aurora.backend.cart.entity.CartItem;
import com.aurora.backend.cart.repository.CartRepository;
import com.aurora.backend.checkout.entity.CheckoutIdempotencyKey;
import com.aurora.backend.checkout.repository.CheckoutIdempotencyKeyRepository;
import com.aurora.backend.catalog.product.entity.Product;
import com.aurora.backend.catalog.product.entity.ProductVariant;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.inventory.entity.Inventory;
import com.aurora.backend.inventory.entity.StockMovement;
import com.aurora.backend.inventory.entity.StockMovementType;
import com.aurora.backend.inventory.repository.InventoryRepository;
import com.aurora.backend.inventory.repository.StockMovementRepository;
import com.aurora.backend.messaging.AuroraTopics;
import com.aurora.backend.messaging.event.OrderCreatedEvent;
import com.aurora.backend.messaging.outbox.OutboxEventRecorder;
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
import com.aurora.backend.promotion.service.CouponService;
import com.aurora.backend.user.entity.User;

import org.springframework.dao.DataIntegrityViolationException;
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
    private final CheckoutIdempotencyKeyRepository idempotencyRepository;
    private final AuditLogService auditLogService;
    private final OutboxEventRecorder outboxRecorder;

    private static final String CURRENCY = "USD";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 120;   // matches the DB column width

    public CheckoutService(
            CartRepository cartRepository,
            InventoryRepository inventoryRepository,
            StockMovementRepository stockMovementRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            CouponService couponService,
            CheckoutIdempotencyKeyRepository idempotencyRepository,
            AuditLogService auditLogService,
            OutboxEventRecorder outboxRecorder
    ) {
        this.cartRepository = cartRepository;
        this.inventoryRepository = inventoryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.couponService = couponService;
        this.idempotencyRepository = idempotencyRepository;
        this.auditLogService = auditLogService;
        this.outboxRecorder = outboxRecorder;
    }

    @Transactional
    public OrderResponse confirmCheckout(User user, String idempotencyKey) {
        // Idempotent retry (OWASP A04): if the client re-submits with an Idempotency-Key we've
        // already fulfilled, replay the original order instead of creating a second one. A
        // committed key always points at a real order (it's written together with the order),
        // so this returns the identical result a double-click / network retry expects.
        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            Optional<CheckoutIdempotencyKey> seen =
                    idempotencyRepository.findByUserIdAndIdempotencyKey(user.getId(), key);
            if (seen.isPresent()) {
                return OrderResponse.from(seen.get().getOrder());
            }
        }

        // Email-verification gate (OWASP A07): the sole order-creation path. The user is resolved
        // fresh per-request by CurrentUserService, so this reads live DB state — a stale/early JWT
        // can't bypass it. Verification is a soft per-action gate, not an authentication boundary.
        if (!user.isEmailVerified()) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "EMAIL_NOT_VERIFIED",
                    "Please verify your email before placing an order."
            );
        }

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

        // Acquire the inventory row locks UP FRONT in a deterministic (variant-id sorted) order.
        // Cart items are an unordered bag, so two concurrent checkouts sharing variants could
        // otherwise lock the same rows in opposite order and deadlock; a stable global order
        // makes that impossible. The per-item decrement below re-reads the now-locked rows.
        cart.getItems().stream()
                .map(item -> item.getVariant().getId())
                .distinct()
                .sorted()
                .forEach(inventoryRepository::findByVariantIdForUpdate);

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

        if (key != null) {
            recordIdempotencyKey(user, key, savedOrder);
        }

        if (coupon != null) {
            // Atomic, row-locked redemption (re-checks usage limits under the lock) so the
            // global/per-user limits can't be beaten by concurrent checkouts (OWASP A04).
            couponService.redeem(coupon, user);
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

        outboxRecorder.record(
                "ORDER",
                savedOrder.getOrderNumber(),
                "ORDER_CREATED",
                AuroraTopics.ORDER_CREATED,
                savedOrder.getOrderNumber(),
                OrderCreatedEvent.of(
                        savedOrder.getId(),
                        savedOrder.getOrderNumber(),
                        user.getEmail(),
                        user.getFirstName() + " " + user.getLastName(),
                        user.getPhone(),
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

    /**
     * Treats a missing/blank Idempotency-Key as "not supplied" (the flow is then non-idempotent).
     * Rejects an over-length key up front with a clear 400 rather than letting it overflow the
     * VARCHAR(120) column and surface later as a misleading "duplicate checkout" 409.
     */
    private String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters."
            );
        }
        return trimmed;
    }

    /**
     * Binds the key to the order it produced. The UNIQUE(user_id, key) constraint is the
     * enforcement point: if a truly-concurrent request already inserted this key, the flush
     * fails and the whole checkout transaction rolls back (no duplicate order/charge). The
     * client's retry then takes the replay path above. A clean 409 lets it do so — but ONLY for
     * a genuine unique violation (SQLState 23505): any other integrity failure is rethrown so it
     * is never masked as a duplicate-checkout race.
     */
    private void recordIdempotencyKey(User user, String key, Order order) {
        try {
            idempotencyRepository.saveAndFlush(new CheckoutIdempotencyKey(user, key, order));
        } catch (DataIntegrityViolationException violation) {
            if (isUniqueViolation(violation)) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "DUPLICATE_CHECKOUT",
                        "A checkout with this Idempotency-Key is already being processed. Please retry."
                );
            }
            throw violation;
        }
    }

    /**
     * Walks the cause chain for a SQL unique-violation (SQLState 23505). On the idempotency
     * table the only unique constraint reachable here is {@code (user_id, idempotency_key)}, so a
     * 23505 unambiguously means a duplicate key — never a different constraint.
     */
    private boolean isUniqueViolation(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
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
        // Lock the inventory row for the rest of the transaction and re-check under the
        // lock, so concurrent checkouts of the same variant serialize instead of both
        // decrementing the same units (overselling). The earlier ensureStockAvailable
        // check is only a fast, unlocked pre-validation.
        Inventory inventory = inventoryRepository.findByVariantIdForUpdate(variant.getId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.CONFLICT,
                        "INVENTORY_NOT_AVAILABLE",
                        "Inventory is not available for variant " + variant.getSku() + "."
                ));

        if (inventory.getAvailableQuantity() < quantity) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_STOCK",
                    "Not enough available stock for variant " + variant.getSku() + "."
            );
        }

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
