package com.aurora.backend.cart.repository;

import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.cart.entity.Cart;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByUserId(UUID userId);
}
