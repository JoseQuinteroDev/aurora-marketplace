package com.aurora.backend.catalog.brand.dto;

import java.util.UUID;

import com.aurora.backend.catalog.brand.entity.Brand;

public record BrandResponse(
        UUID id,
        String name,
        String slug,
        boolean active
) {

    public static BrandResponse from(Brand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getName(),
                brand.getSlug(),
                brand.isActive()
        );
    }
}
