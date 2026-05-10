package com.aurora.backend.catalog.product.repository;

import java.util.UUID;

import com.aurora.backend.catalog.product.entity.ProductImage;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {
}
