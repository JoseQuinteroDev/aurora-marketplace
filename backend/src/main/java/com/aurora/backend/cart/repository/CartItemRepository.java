package com.aurora.backend.cart.repository;

import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.cart.entity.CartItem;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    Optional<CartItem> findByIdAndCartUserId(UUID id, UUID userId);
}
