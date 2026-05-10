package com.aurora.backend.catalog.product.repository;

import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.catalog.product.entity.ProductVariant;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    Optional<ProductVariant> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, UUID id);
}
