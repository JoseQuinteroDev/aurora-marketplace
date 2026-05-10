package com.aurora.backend.catalog.category.service;

import java.util.List;
import java.util.UUID;

import com.aurora.backend.catalog.category.dto.CategoryRequest;
import com.aurora.backend.catalog.category.dto.CategoryResponse;
import com.aurora.backend.catalog.category.entity.Category;
import com.aurora.backend.catalog.category.repository.CategoryRepository;
import com.aurora.backend.catalog.common.SlugUtils;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String slug = SlugUtils.fromOptional(request.slug(), request.name());
        ensureSlugAvailable(slug);

        Category category = new Category(request.name().trim(), slug, activeOrDefault(request.active()));
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category category = findCategory(id);
        String slug = SlugUtils.fromOptional(request.slug(), request.name());

        if (categoryRepository.existsBySlugAndIdNot(slug, id)) {
            throw duplicateSlugException();
        }

        category.update(request.name().trim(), slug, activeOrDefault(request.active()));
        return CategoryResponse.from(category);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        Category category = findCategory(id);
        category.deactivate();
    }

    @Transactional(readOnly = true)
    public Category getActiveCategory(UUID id) {
        return categoryRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new NotFoundException("Active category", id));
    }

    private Category findCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category", id));
    }

    private void ensureSlugAvailable(String slug) {
        if (categoryRepository.existsBySlug(slug)) {
            throw duplicateSlugException();
        }
    }

    private BusinessException duplicateSlugException() {
        return new BusinessException(HttpStatus.CONFLICT, "CATEGORY_SLUG_EXISTS", "Category slug already exists.");
    }

    private boolean activeOrDefault(Boolean active) {
        return active == null || active;
    }
}
