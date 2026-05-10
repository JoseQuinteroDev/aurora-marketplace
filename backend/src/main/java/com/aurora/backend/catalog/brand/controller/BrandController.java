package com.aurora.backend.catalog.brand.controller;

import java.util.List;

import com.aurora.backend.catalog.brand.dto.BrandResponse;
import com.aurora.backend.catalog.brand.service.BrandService;
import com.aurora.backend.common.api.ApiResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brands")
public class BrandController {

    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @GetMapping
    public ApiResponse<List<BrandResponse>> listBrands() {
        return ApiResponse.success("Brands retrieved successfully.", brandService.listActiveBrands());
    }
}
