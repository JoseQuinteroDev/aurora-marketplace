package com.aurora.backend.catalog.product.controller;

import java.util.List;

import com.aurora.backend.catalog.product.dto.ProductResponse;
import com.aurora.backend.catalog.product.service.ProductService;
import com.aurora.backend.common.api.ApiResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ApiResponse<List<ProductResponse>> listProducts() {
        return ApiResponse.success("Products retrieved successfully.", productService.listActiveProducts());
    }

    @GetMapping("/search")
    public ApiResponse<List<ProductResponse>> searchProducts(@RequestParam String q) {
        return ApiResponse.success("Products retrieved successfully.", productService.searchActiveProducts(q));
    }

    @GetMapping("/{slug}")
    public ApiResponse<ProductResponse> getProduct(@PathVariable String slug) {
        return ApiResponse.success("Product retrieved successfully.", productService.getActiveProductBySlug(slug));
    }
}
