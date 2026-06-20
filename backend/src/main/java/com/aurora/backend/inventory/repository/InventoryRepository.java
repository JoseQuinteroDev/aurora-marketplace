package com.aurora.backend.inventory.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.inventory.entity.Inventory;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByVariantId(UUID variantId);

    /**
     * Loads the inventory row with a pessimistic write lock (SELECT ... FOR UPDATE)
     * so the read-check-decrement in checkout is serialized across concurrent
     * checkouts of the same variant — preventing overselling.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.variant.id = :variantId")
    Optional<Inventory> findByVariantIdForUpdate(@Param("variantId") UUID variantId);

    boolean existsByVariantId(UUID variantId);

    List<Inventory> findAllByOrderByUpdatedAtDesc();

    @Query("select count(inventory) from Inventory inventory where inventory.availableQuantity <= inventory.lowStockThreshold")
    long countLowStockItems();
}
