package com.aurora.backend.review.repository;

import java.util.List;
import java.util.UUID;

import com.aurora.backend.review.entity.Review;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByProductIdAndActiveTrueOrderByCreatedAtDesc(UUID productId);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);
}
