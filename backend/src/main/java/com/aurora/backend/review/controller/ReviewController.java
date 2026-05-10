package com.aurora.backend.review.controller;

import java.util.List;
import java.util.UUID;

import com.aurora.backend.common.api.ApiResponse;
import com.aurora.backend.review.dto.ReviewRequest;
import com.aurora.backend.review.dto.ReviewResponse;
import com.aurora.backend.review.service.ReviewService;
import com.aurora.backend.security.CurrentUserService;
import com.aurora.backend.user.entity.User;
import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUserService currentUserService;

    public ReviewController(ReviewService reviewService, CurrentUserService currentUserService) {
        this.reviewService = reviewService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<List<ReviewResponse>> listReviews(@PathVariable UUID productId) {
        return ApiResponse.success("Reviews retrieved successfully.", reviewService.listProductReviews(productId));
    }

    @PostMapping
    public ApiResponse<ReviewResponse> createReview(
            Authentication authentication,
            @PathVariable UUID productId,
            @Valid @RequestBody ReviewRequest request
    ) {
        User user = currentUserService.getCurrentUser(authentication);
        return ApiResponse.success("Review created successfully.", reviewService.createReview(user, productId, request));
    }
}
