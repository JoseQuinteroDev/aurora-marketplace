package com.aurora.backend.cart.controller;

import java.util.UUID;

import com.aurora.backend.cart.dto.AddCartItemRequest;
import com.aurora.backend.cart.dto.ApplyCouponRequest;
import com.aurora.backend.cart.dto.CartResponse;
import com.aurora.backend.cart.dto.UpdateCartItemRequest;
import com.aurora.backend.cart.service.CartService;
import com.aurora.backend.common.api.ApiResponse;
import com.aurora.backend.security.CurrentUserService;
import com.aurora.backend.user.entity.User;
import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;
    private final CurrentUserService currentUserService;

    public CartController(CartService cartService, CurrentUserService currentUserService) {
        this.cartService = cartService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<CartResponse> getCart(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        return ApiResponse.success("Cart retrieved successfully.", cartService.getCart(user));
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> addItem(
            Authentication authentication,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        User user = currentUserService.getCurrentUser(authentication);
        return ApiResponse.success("Cart item added successfully.", cartService.addItem(user, request));
    }

    @PatchMapping("/items/{itemId}")
    public ApiResponse<CartResponse> updateItem(
            Authentication authentication,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        User user = currentUserService.getCurrentUser(authentication);
        return ApiResponse.success("Cart item updated successfully.", cartService.updateItem(user, itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<CartResponse> removeItem(Authentication authentication, @PathVariable UUID itemId) {
        User user = currentUserService.getCurrentUser(authentication);
        return ApiResponse.success("Cart item removed successfully.", cartService.removeItem(user, itemId));
    }

    @DeleteMapping
    public ApiResponse<CartResponse> clearCart(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        return ApiResponse.success("Cart cleared successfully.", cartService.clearCart(user));
    }

    @PostMapping("/apply-coupon")
    public ApiResponse<CartResponse> applyCoupon(
            Authentication authentication,
            @Valid @RequestBody ApplyCouponRequest request
    ) {
        User user = currentUserService.getCurrentUser(authentication);
        return ApiResponse.success("Coupon applied successfully.", cartService.applyCoupon(user, request));
    }

    @DeleteMapping("/coupon")
    public ApiResponse<CartResponse> removeCoupon(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        return ApiResponse.success("Coupon removed successfully.", cartService.removeCoupon(user));
    }
}
