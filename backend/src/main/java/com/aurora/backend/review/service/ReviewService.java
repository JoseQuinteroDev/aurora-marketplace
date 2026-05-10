package com.aurora.backend.review.service;

import java.util.List;
import java.util.UUID;

import com.aurora.backend.catalog.product.entity.Product;
import com.aurora.backend.catalog.product.repository.ProductRepository;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;
import com.aurora.backend.review.dto.ReviewRequest;
import com.aurora.backend.review.dto.ReviewResponse;
import com.aurora.backend.review.entity.Review;
import com.aurora.backend.review.repository.ReviewRepository;
import com.aurora.backend.user.entity.User;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;

    public ReviewService(ReviewRepository reviewRepository, ProductRepository productRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listProductReviews(UUID productId) {
        getActiveProduct(productId);
        return reviewRepository.findByProductIdAndActiveTrueOrderByCreatedAtDesc(productId).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    @Transactional
    public ReviewResponse createReview(User user, UUID productId, ReviewRequest request) {
        Product product = getActiveProduct(productId);

        if (reviewRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "REVIEW_ALREADY_EXISTS",
                    "User has already reviewed this product."
            );
        }

        Review review = new Review(
                user,
                product,
                request.rating(),
                normalizeNullableText(request.title()),
                normalizeNullableText(request.comment())
        );

        return ReviewResponse.from(reviewRepository.save(review));
    }

    @Transactional
    public void deleteReview(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Review", reviewId));
        review.deactivate();
    }

    private Product getActiveProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));

        if (!product.isActive()) {
            throw new NotFoundException("Active product", productId);
        }

        return product;
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
