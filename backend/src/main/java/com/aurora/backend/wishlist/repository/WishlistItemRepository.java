package com.aurora.backend.wishlist.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.wishlist.entity.WishlistItem;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, UUID> {

    List<WishlistItem> findByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    Optional<WishlistItem> findByUserIdAndProductId(UUID userId, UUID productId);
}
