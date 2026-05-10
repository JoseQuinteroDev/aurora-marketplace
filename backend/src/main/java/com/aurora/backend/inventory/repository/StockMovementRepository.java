package com.aurora.backend.inventory.repository;

import java.util.List;
import java.util.UUID;

import com.aurora.backend.inventory.entity.StockMovement;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    List<StockMovement> findByVariantIdOrderByCreatedAtDesc(UUID variantId);

    List<StockMovement> findAllByOrderByCreatedAtDesc();
}
