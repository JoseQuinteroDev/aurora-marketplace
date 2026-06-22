package com.aurora.backend.review.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.aurora.backend.catalog.product.entity.Product;
import com.aurora.backend.catalog.product.repository.ProductRepository;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;
import com.aurora.backend.review.dto.ReviewRequest;
import com.aurora.backend.review.dto.ReviewResponse;
import com.aurora.backend.review.repository.ReviewRepository;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.role.Role;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReviewService} integrity rules: one review per user per
 * product, and reviews are only readable/creatable for an active product (a
 * hidden product leaks nothing). (OWASP A04 — business logic.)
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock private ReviewRepository reviewRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks
    private ReviewService reviewService;

    private User customer() {
        return new User("rev@aurora.test", "hash", "Reva", "Yew", Role.CUSTOMER, true);
    }

    private Product product(boolean active) {
        return new Product("Lamp", "lamp", null, null, new BigDecimal("10.00"), active, false, null, null);
    }

    private ReviewRequest request() {
        return new ReviewRequest(5, "Great", "Loved it");
    }

    @Test
    void createReviewSucceedsForAnActiveProductNotYetReviewed() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product(true)));
        when(reviewRepository.existsByUserIdAndProductId(any(), any())).thenReturn(false);
        when(reviewRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        ReviewResponse response = reviewService.createReview(customer(), PRODUCT_ID, request());

        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.authorName()).isEqualTo("Reva");
        // A fresh review is never auto-marked as a verified purchase.
        assertThat(response.verifiedPurchase()).isFalse();
    }

    @Test
    void createReviewRejectsASecondReviewBySameUser() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product(true)));
        when(reviewRepository.existsByUserIdAndProductId(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(customer(), PRODUCT_ID, request()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REVIEW_ALREADY_EXISTS"));

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReviewOnInactiveProductIsNotFound() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product(false)));

        assertThatThrownBy(() -> reviewService.createReview(customer(), PRODUCT_ID, request()))
                .isInstanceOf(NotFoundException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void listingReviewsOfAnInactiveProductIsNotFound() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product(false)));

        assertThatThrownBy(() -> reviewService.listProductReviews(PRODUCT_ID))
                .isInstanceOf(NotFoundException.class);

        verify(reviewRepository, never()).findByProductIdAndActiveTrueOrderByCreatedAtDesc(any());
    }
}
