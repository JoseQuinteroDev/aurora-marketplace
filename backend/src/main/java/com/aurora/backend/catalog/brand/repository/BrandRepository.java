package com.aurora.backend.catalog.brand.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.catalog.brand.entity.Brand;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, UUID> {

    List<Brand> findByActiveTrueOrderByNameAsc();

    Optional<Brand> findByIdAndActiveTrue(UUID id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);
}
