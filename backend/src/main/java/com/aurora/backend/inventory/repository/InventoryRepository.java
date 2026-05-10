package com.aurora.backend.inventory.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.inventory.entity.Inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByVariantId(UUID variantId);

    boolean existsByVariantId(UUID variantId);

    List<Inventory> findAllByOrderByUpdatedAtDesc();

    @Query("select count(inventory) from Inventory inventory where inventory.availableQuantity <= inventory.lowStockThreshold")
    long countLowStockItems();
}
