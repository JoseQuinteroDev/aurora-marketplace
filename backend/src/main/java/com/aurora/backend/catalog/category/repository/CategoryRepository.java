package com.aurora.backend.catalog.category.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.catalog.category.entity.Category;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByActiveTrueOrderByNameAsc();

    Optional<Category> findByIdAndActiveTrue(UUID id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);
}
