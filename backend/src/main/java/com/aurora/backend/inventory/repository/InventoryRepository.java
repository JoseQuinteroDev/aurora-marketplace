package com.aurora.backend.inventory.repository;

import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.inventory.entity.Inventory;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByVariantId(UUID variantId);

    boolean existsByVariantId(UUID variantId);
}
