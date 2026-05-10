package com.aurora.backend.catalog.category.dto;

import java.util.UUID;

import com.aurora.backend.catalog.category.entity.Category;

public record CategoryResponse(
        UUID id,
        String name,
        String slug,
        boolean active
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.isActive()
        );
    }
}
