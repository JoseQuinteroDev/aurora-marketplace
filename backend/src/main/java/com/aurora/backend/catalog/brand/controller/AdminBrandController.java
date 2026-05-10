package com.aurora.backend.catalog.brand.controller;

import java.util.UUID;

import com.aurora.backend.catalog.brand.dto.BrandRequest;
import com.aurora.backend.catalog.brand.dto.BrandResponse;
import com.aurora.backend.catalog.brand.service.BrandService;
import com.aurora.backend.common.api.ApiResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/brands")
public class AdminBrandController {

    private final BrandService brandService;

    public AdminBrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(@Valid @RequestBody BrandRequest request) {
        BrandResponse response = brandService.createBrand(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Brand created successfully.", response));
    }

    @PutMapping("/{id}")
    public ApiResponse<BrandResponse> updateBrand(
            @PathVariable UUID id,
            @Valid @RequestBody BrandRequest request
    ) {
        return ApiResponse.success("Brand updated successfully.", brandService.updateBrand(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteBrand(@PathVariable UUID id) {
        brandService.deleteBrand(id);
        return ApiResponse.success("Brand deleted successfully.");
    }
}
