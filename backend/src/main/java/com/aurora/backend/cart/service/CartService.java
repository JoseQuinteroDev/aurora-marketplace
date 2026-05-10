package com.aurora.backend.cart.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.cart.dto.AddCartItemRequest;
import com.aurora.backend.cart.dto.ApplyCouponRequest;
import com.aurora.backend.cart.dto.CartCouponResponse;
import com.aurora.backend.cart.dto.CartItemResponse;
import com.aurora.backend.cart.dto.CartResponse;
import com.aurora.backend.cart.dto.UpdateCartItemRequest;
import com.aurora.backend.cart.entity.Cart;
import com.aurora.backend.cart.entity.CartItem;
import com.aurora.backend.cart.repository.CartItemRepository;
import com.aurora.backend.cart.repository.CartRepository;
import com.aurora.backend.catalog.product.entity.Product;
import com.aurora.backend.catalog.product.entity.ProductVariant;
import com.aurora.backend.catalog.product.repository.ProductVariantRepository;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;
import com.aurora.backend.inventory.entity.Inventory;
import com.aurora.backend.inventory.repository.InventoryRepository;
import com.aurora.backend.promotion.entity.Coupon;
import com.aurora.backend.promotion.service.CouponService;
import com.aurora.backend.user.entity.User;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final CouponService couponService;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductVariantRepository productVariantRepository,
            InventoryRepository inventoryRepository,
            CouponService couponService
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productVariantRepository = productVariantRepository;
        this.inventoryRepository = inventoryRepository;
        this.couponService = couponService;
    }

    @Transactional
    public CartResponse getCart(User user) {
        return toResponse(getOrCreateCart(user));
    }

    @Transactional
    public CartResponse addItem(User user, AddCartItemRequest request) {
        Cart cart = getOrCreateCart(user);
        ProductVariant variant = getActiveVariant(request.variantId());
        int requestedQuantity = request.quantity();

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getVariant().getId().equals(variant.getId()))
                .findFirst();

        int finalQuantity = existingItem.map(item -> item.getQuantity() + requestedQuantity).orElse(requestedQuantity);
        ensureAvailableStock(variant, finalQuantity);

        if (existingItem.isPresent()) {
            existingItem.get().increaseQuantity(requestedQuantity);
        } else {
            cart.addItem(new CartItem(variant, requestedQuantity));
        }

        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(User user, UUID itemId, UpdateCartItemRequest request) {
        CartItem item = cartItemRepository.findByIdAndCartUserId(itemId, user.getId())
                .orElseThrow(() -> new NotFoundException("Cart item", itemId));

        ensureAvailableStock(item.getVariant(), request.quantity());
        item.updateQuantity(request.quantity());
        return toResponse(item.getCart());
    }

    @Transactional
    public CartResponse removeItem(User user, UUID itemId) {
        CartItem item = cartItemRepository.findByIdAndCartUserId(itemId, user.getId())
                .orElseThrow(() -> new NotFoundException("Cart item", itemId));

        Cart cart = item.getCart();
        cart.removeItem(item);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse clearCart(User user) {
        Cart cart = getOrCreateCart(user);
        cart.clearItems();
        return toResponse(cart);
    }

    @Transactional
    public CartResponse applyCoupon(User user, ApplyCouponRequest request) {
        Cart cart = getOrCreateCart(user);
        BigDecimal subtotal = calculateSubtotal(cart);
        Coupon coupon = couponService.validateCouponForCart(request.code(), user, subtotal);
        cart.applyCoupon(coupon);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeCoupon(User user) {
        Cart cart = getOrCreateCart(user);
        cart.removeCoupon();
        return toResponse(cart);
    }

    private Cart getOrCreateCart(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> cartRepository.save(new Cart(user)));
    }

    private ProductVariant getActiveVariant(UUID variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new NotFoundException("Product variant", variantId));

        if (!variant.isActive() || !variant.getProduct().isActive()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PRODUCT_VARIANT_INACTIVE", "Product variant is not active.");
        }

        return variant;
    }

    private void ensureAvailableStock(ProductVariant variant, int quantity) {
        Inventory inventory = inventoryRepository.findByVariantId(variant.getId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.CONFLICT,
                        "INVENTORY_NOT_AVAILABLE",
                        "Inventory is not available for this variant."
                ));

        if (inventory.getAvailableQuantity() < quantity) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_STOCK",
                    "Not enough available stock for this variant."
            );
        }
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        BigDecimal subtotal = items.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal discount = couponService.calculateDiscount(cart.getCoupon(), cart.getUser(), subtotal);
        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        CartCouponResponse coupon = cart.getCoupon() == null ? null : CartCouponResponse.from(cart.getCoupon());

        return new CartResponse(cart.getId(), items, coupon, subtotal, discount, total);
    }

    private CartItemResponse toItemResponse(CartItem item) {
        ProductVariant variant = item.getVariant();
        Product product = variant.getProduct();
        BigDecimal unitPrice = effectivePrice(variant, product);
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP);

        return new CartItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getSlug(),
                variant.getId(),
                variant.getSku(),
                variant.getName(),
                item.getQuantity(),
                unitPrice,
                lineTotal
        );
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
}
