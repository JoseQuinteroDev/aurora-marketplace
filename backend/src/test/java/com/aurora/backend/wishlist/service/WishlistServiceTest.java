package com.aurora.backend.wishlist.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.catalog.product.entity.Product;
import com.aurora.backend.catalog.product.repository.ProductRepository;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.role.Role;
import com.aurora.backend.wishlist.entity.WishlistItem;
import com.aurora.backend.wishlist.repository.WishlistItemRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Unit tests for {@link WishlistService}: items belong to a product that must be
 * active, are de-duplicated per user, and are removed via a user-scoped lookup so
 * one customer cannot delete another's wishlist entry. (A04 + A01.)
 */
@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    @Mock private WishlistItemRepository wishlistItemRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks
    private WishlistService wishlistService;

    private User customer() {
        return new User("wish@aurora.test", "hash", "Wish", "List", Role.CUSTOMER, true);
    }

    private Product product(boolean active) {
        return new Product("Lamp", "lamp", null, null, new BigDecimal("10.00"), active, false, null, null);
    }

    @Test
    void addingAProductAlreadyInTheWishlistIsRejected() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product(true)));
        when(wishlistItemRepository.existsByUserIdAndProductId(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> wishlistService.addToWishlist(customer(), PRODUCT_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("WISHLIST_ITEM_EXISTS"));

        verify(wishlistItemRepository, never()).save(any());
    }

    @Test
    void addingAnInactiveProductIsNotFound() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product(false)));

        assertThatThrownBy(() -> wishlistService.addToWishlist(customer(), PRODUCT_ID))
                .isInstanceOf(NotFoundException.class);

        verify(wishlistItemRepository, never()).save(any());
    }

    @Test
    void removingAnItemThatIsNotTheUsersIsNotFound() {
        // The lookup is user-scoped (findByUserIdAndProductId); a non-owner gets nothing.
        when(wishlistItemRepository.findByUserIdAndProductId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.removeFromWishlist(customer(), PRODUCT_ID))
                .isInstanceOf(NotFoundException.class);

        verify(wishlistItemRepository, never()).delete(any(WishlistItem.class));
    }
}
