package com.aurora.backend.catalog.product.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.catalog.product.entity.Product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByActiveTrueOrderByCreatedAtDesc();

    /** All products, active and inactive, newest first — the admin management list. */
    List<Product> findAllByOrderByCreatedAtDesc();

    Optional<Product> findBySlugAndActiveTrue(String slug);

    Optional<Product> findBySlug(String slug);

    long countByActiveTrue();

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    @Query("""
            select product from Product product
            where product.active = true
              and (
                    lower(product.name) like lower(concat('%', :query, '%'))
                 or lower(product.shortDescription) like lower(concat('%', :query, '%'))
                 or lower(product.description) like lower(concat('%', :query, '%'))
              )
            order by product.name asc
            """)
    List<Product> searchActive(@Param("query") String query);
}
