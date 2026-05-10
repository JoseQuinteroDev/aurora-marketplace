package com.aurora.backend.wishlist.service;

import java.util.List;
import java.util.UUID;

import com.aurora.backend.catalog.product.entity.Product;
import com.aurora.backend.catalog.product.repository.ProductRepository;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.wishlist.dto.WishlistItemResponse;
import com.aurora.backend.wishlist.entity.WishlistItem;
import com.aurora.backend.wishlist.repository.WishlistItemRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;

    public WishlistService(WishlistItemRepository wishlistItemRepository, ProductRepository productRepository) {
        this.wishlistItemRepository = wishlistItemRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<WishlistItemResponse> getWishlist(User user) {
        return wishlistItemRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(WishlistItemResponse::from)
                .toList();
    }

    @Transactional
    public WishlistItemResponse addToWishlist(User user, UUID productId) {
        Product product = getActiveProduct(productId);

        if (wishlistItemRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "WISHLIST_ITEM_EXISTS",
                    "Product is already in the wishlist."
            );
        }

        return WishlistItemResponse.from(wishlistItemRepository.save(new WishlistItem(user, product)));
    }

    @Transactional
    public void removeFromWishlist(User user, UUID productId) {
        WishlistItem item = wishlistItemRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new NotFoundException("Wishlist item", productId));

        wishlistItemRepository.delete(item);
    }

    private Product getActiveProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));

        if (!product.isActive()) {
            throw new NotFoundException("Active product", productId);
        }

        return product;
    }
}
