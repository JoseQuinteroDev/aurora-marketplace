package com.aurora.backend.catalog.brand.service;

import java.util.List;
import java.util.UUID;

import com.aurora.backend.catalog.brand.dto.BrandRequest;
import com.aurora.backend.catalog.brand.dto.BrandResponse;
import com.aurora.backend.catalog.brand.entity.Brand;
import com.aurora.backend.catalog.brand.repository.BrandRepository;
import com.aurora.backend.catalog.common.SlugUtils;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrandService {

    private final BrandRepository brandRepository;

    public BrandService(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> listActiveBrands() {
        return brandRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(BrandResponse::from)
                .toList();
    }

    @Transactional
    public BrandResponse createBrand(BrandRequest request) {
        String slug = SlugUtils.fromOptional(request.slug(), request.name());
        ensureSlugAvailable(slug);

        Brand brand = new Brand(request.name().trim(), slug, activeOrDefault(request.active()));
        return BrandResponse.from(brandRepository.save(brand));
    }

    @Transactional
    public BrandResponse updateBrand(UUID id, BrandRequest request) {
        Brand brand = findBrand(id);
        String slug = SlugUtils.fromOptional(request.slug(), request.name());

        if (brandRepository.existsBySlugAndIdNot(slug, id)) {
            throw duplicateSlugException();
        }

        brand.update(request.name().trim(), slug, activeOrDefault(request.active()));
        return BrandResponse.from(brand);
    }

    @Transactional
    public void deleteBrand(UUID id) {
        Brand brand = findBrand(id);
        brand.deactivate();
    }

    @Transactional(readOnly = true)
    public Brand getActiveBrand(UUID id) {
        return brandRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new NotFoundException("Active brand", id));
    }

    private Brand findBrand(UUID id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Brand", id));
    }

    private void ensureSlugAvailable(String slug) {
        if (brandRepository.existsBySlug(slug)) {
            throw duplicateSlugException();
        }
    }

    private BusinessException duplicateSlugException() {
        return new BusinessException(HttpStatus.CONFLICT, "BRAND_SLUG_EXISTS", "Brand slug already exists.");
    }

    private boolean activeOrDefault(Boolean active) {
        return active == null || active;
    }
}
