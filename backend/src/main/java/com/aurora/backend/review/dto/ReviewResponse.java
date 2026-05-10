package com.aurora.backend.review.dto;

import java.time.Instant;
import java.util.UUID;

import com.aurora.backend.review.entity.Review;

public record ReviewResponse(
        UUID id,
        UUID productId,
        String authorName,
        int rating,
        String title,
        String comment,
        boolean verifiedPurchase,
        Instant createdAt
) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getProduct().getId(),
                review.getUser().getFirstName(),
                review.getRating(),
                review.getTitle(),
                review.getComment(),
                review.isVerifiedPurchase(),
                review.getCreatedAt()
        );
    }
}
