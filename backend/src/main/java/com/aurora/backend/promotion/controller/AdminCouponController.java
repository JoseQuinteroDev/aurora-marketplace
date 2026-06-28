package com.aurora.backend.promotion.controller;

import java.util.List;
import java.util.UUID;

import com.aurora.backend.common.api.ApiResponse;
import com.aurora.backend.promotion.dto.CouponRequest;
import com.aurora.backend.promotion.dto.CouponResponse;
import com.aurora.backend.promotion.service.CouponService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/coupons")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCouponController {

    private final CouponService couponService;

    public AdminCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    public ApiResponse<List<CouponResponse>> listCoupons() {
        return ApiResponse.success("Coupons retrieved successfully.", couponService.listCoupons());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(@Valid @RequestBody CouponRequest request) {
        CouponResponse response = couponService.createCoupon(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Coupon created successfully.", response));
    }

    @PutMapping("/{id}")
    public ApiResponse<CouponResponse> updateCoupon(
            @PathVariable UUID id,
            @Valid @RequestBody CouponRequest request
    ) {
        return ApiResponse.success("Coupon updated successfully.", couponService.updateCoupon(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCoupon(@PathVariable UUID id) {
        couponService.deleteCoupon(id);
        return ApiResponse.success("Coupon deleted successfully.");
    }
}
