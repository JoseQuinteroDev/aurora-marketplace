package com.aurora.backend.catalog.category.controller;

import java.util.List;

import com.aurora.backend.catalog.category.dto.CategoryResponse;
import com.aurora.backend.catalog.category.service.CategoryService;
import com.aurora.backend.common.api.ApiResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> listCategories() {
        return ApiResponse.success("Categories retrieved successfully.", categoryService.listActiveCategories());
    }
}
