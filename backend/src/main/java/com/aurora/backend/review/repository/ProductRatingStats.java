package com.aurora.backend.review.repository;

import java.util.UUID;

/**
 * Aggregated review stats for a product, produced by a single grouped query so the
 * product list endpoint can expose ratings without an N+1 of per-product lookups.
 */
public interface ProductRatingStats {

    UUID getProductId();

    Double getAverageRating();

    long getReviewCount();
}
