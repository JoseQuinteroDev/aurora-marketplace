package com.aurora.backend.review.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.aurora.backend.review.entity.Review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByProductIdAndActiveTrueOrderByCreatedAtDesc(UUID productId);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    /**
     * Average rating + count of active reviews, grouped per product, for a batch of
     * product ids. One query for the whole product list — no N+1. Products with no
     * active reviews simply do not appear in the result.
     */
    @Query("""
            SELECT r.product.id AS productId, AVG(r.rating) AS averageRating, COUNT(r) AS reviewCount
            FROM Review r
            WHERE r.active = true AND r.product.id IN :productIds
            GROUP BY r.product.id
            """)
    List<ProductRatingStats> findRatingStatsByProductIds(@Param("productIds") Collection<UUID> productIds);
}
