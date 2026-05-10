package com.aurora.backend.wishlist.controller;

import java.util.List;
import java.util.UUID;

import com.aurora.backend.common.api.ApiResponse;
import com.aurora.backend.security.CurrentUserService;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.wishlist.dto.WishlistItemResponse;
import com.aurora.backend.wishlist.service.WishlistService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;
    private final CurrentUserService currentUserService;

    public WishlistController(WishlistService wishlistService, CurrentUserService currentUserService) {
        this.wishlistService = wishlistService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<List<WishlistItemResponse>> getWishlist(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        return ApiResponse.success("Wishlist retrieved successfully.", wishlistService.getWishlist(user));
    }

    @PostMapping("/{productId}")
    public ApiResponse<WishlistItemResponse> addToWishlist(
            Authentication authentication,
            @PathVariable UUID productId
    ) {
        User user = currentUserService.getCurrentUser(authentication);
        return ApiResponse.success("Product added to wishlist.", wishlistService.addToWishlist(user, productId));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> removeFromWishlist(Authentication authentication, @PathVariable UUID productId) {
        User user = currentUserService.getCurrentUser(authentication);
        wishlistService.removeFromWishlist(user, productId);
        return ApiResponse.success("Product removed from wishlist.");
    }
}
