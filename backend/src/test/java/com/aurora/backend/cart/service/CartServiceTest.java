package com.aurora.backend.cart.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.cart.dto.AddCartItemRequest;
import com.aurora.backend.cart.dto.UpdateCartItemRequest;
import com.aurora.backend.cart.entity.Cart;
import com.aurora.backend.cart.repository.CartItemRepository;
import com.aurora.backend.cart.repository.CartRepository;
import com.aurora.backend.catalog.product.entity.Product;
import com.aurora.backend.catalog.product.entity.ProductVariant;
import com.aurora.backend.catalog.product.repository.ProductVariantRepository;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;
import com.aurora.backend.inventory.entity.Inventory;
import com.aurora.backend.inventory.repository.InventoryRepository;
import com.aurora.backend.promotion.service.CouponService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CartService}: server-side pricing/stock validation at
 * add-to-cart (A04) and the per-user ownership of cart items (A01 — IDOR).
 *
 * <p>Cart items can only be mutated via the owner-scoped
 * {@link CartItemRepository#findByIdAndCartUserId}, so one customer cannot touch
 * another's cart by item id — the same IDOR control proven for orders, extended
 * here to the cart resource.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final UUID ITEM_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID VARIANT_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final BigDecimal PRICE = new BigDecimal("50.00");

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private CouponService couponService;

    @InjectMocks
    private CartService cartService;

    private User customer() {
        return new User("cart@aurora.test", "hash", "Car", "Tel", Role.CUSTOMER, true);
    }

    private ProductVariant variant(boolean active) {
        ProductVariant variant = new ProductVariant("SKU-1", "Default", null, null, active);
        Product product = new Product("Lamp", "lamp", null, null, PRICE, true, false, null, null);
        product.addVariant(variant);
        return variant;
    }

    @Test
    void addItemRejectsAnInactiveVariant() {
        User user = customer();
        when(cartRepository.findByUserId(any())).thenReturn(Optional.of(new Cart(user)));
        when(productVariantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant(false)));

        assertThatThrownBy(() -> cartService.addItem(user, new AddCartItemRequest(VARIANT_ID, 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("PRODUCT_VARIANT_INACTIVE"));
    }

    @Test
    void addItemRejectsInsufficientStock() {
        User user = customer();
        ProductVariant variant = variant(true);
        when(cartRepository.findByUserId(any())).thenReturn(Optional.of(new Cart(user)));
        when(productVariantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
        when(inventoryRepository.findByVariantId(any())).thenReturn(Optional.of(new Inventory(variant, 0, 0, 2)));

        assertThatThrownBy(() -> cartService.addItem(user, new AddCartItemRequest(VARIANT_ID, 3)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INSUFFICIENT_STOCK"));
    }

    @Test
    void addItemPricesLinesFromTheCatalogNotTheClient() {
        User user = customer();
        ProductVariant variant = variant(true);
        when(cartRepository.findByUserId(any())).thenReturn(Optional.of(new Cart(user)));
        when(productVariantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
        when(inventoryRepository.findByVariantId(any())).thenReturn(Optional.of(new Inventory(variant, 10, 0, 2)));
        when(couponService.calculateDiscount(any(), any(), any())).thenReturn(BigDecimal.ZERO.setScale(2));

        var cart = cartService.addItem(user, new AddCartItemRequest(VARIANT_ID, 2));

        assertThat(cart.items()).singleElement().satisfies(item -> {
            assertThat(item.unitPrice()).isEqualByComparingTo("50.00");
            assertThat(item.lineTotal()).isEqualByComparingTo("100.00");   // 2 × 50.00
        });
        assertThat(cart.subtotal()).isEqualByComparingTo("100.00");
        assertThat(cart.total()).isEqualByComparingTo("100.00");
    }

    @Test
    void updatingAnotherCustomersCartItemIsNotFound() {
        User attacker = customer();
        // The owner-scoped lookup finds nothing for a non-owner → NotFound, never the item.
        when(cartItemRepository.findByIdAndCartUserId(eq(ITEM_ID), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateItem(attacker, ITEM_ID, new UpdateCartItemRequest(9)))
                .isInstanceOf(NotFoundException.class);

        verify(cartItemRepository).findByIdAndCartUserId(eq(ITEM_ID), any());
    }

    @Test
    void removingAnotherCustomersCartItemIsNotFound() {
        User attacker = customer();
        when(cartItemRepository.findByIdAndCartUserId(eq(ITEM_ID), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItem(attacker, ITEM_ID))
                .isInstanceOf(NotFoundException.class);

        verify(cartItemRepository).findByIdAndCartUserId(eq(ITEM_ID), any());
    }
}
