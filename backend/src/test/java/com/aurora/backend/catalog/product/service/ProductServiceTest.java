package com.aurora.backend.catalog.product.service;

import java.util.List;

import com.aurora.backend.catalog.brand.service.BrandService;
import com.aurora.backend.catalog.category.service.CategoryService;
import com.aurora.backend.catalog.product.repository.ProductRepository;
import com.aurora.backend.catalog.product.repository.ProductVariantRepository;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;
import com.aurora.backend.inventory.service.InventoryService;
import com.aurora.backend.review.repository.ReviewRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductService} read paths: search input validation, and
 * the no-N+1 rating-aggregation design (an empty product list must not trigger the
 * rating query at all).
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private CategoryService categoryService;
    @Mock private BrandService brandService;
    @Mock private InventoryService inventoryService;
    @Mock private ReviewRepository reviewRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void searchWithABlankQueryIsRejected() {
        assertThatThrownBy(() -> productService.searchActiveProducts("   "))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("SEARCH_QUERY_REQUIRED"));
    }

    @Test
    void searchWithANullQueryIsRejected() {
        assertThatThrownBy(() -> productService.searchActiveProducts(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void listingWithNoProductsReturnsEmptyAndSkipsTheRatingQuery() {
        when(productRepository.findByActiveTrueOrderByCreatedAtDesc()).thenReturn(List.of());

        assertThat(productService.listActiveProducts()).isEmpty();
        // No products → the grouped rating aggregation must not run (no wasted query).
        verify(reviewRepository, never()).findRatingStatsByProductIds(any());
    }

    @Test
    void fetchingAnUnknownSlugIsNotFound() {
        when(productRepository.findBySlugAndActiveTrue(any())).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> productService.getActiveProductBySlug("ghost-product"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void adminListUsesTheUnfilteredQueryAndSkipsRatingsWhenEmpty() {
        // The admin list pulls from findAllByOrderByCreatedAtDesc (active AND inactive), distinct
        // from the public listActiveProducts (active-only). Empty result must not run the rating query.
        when(productRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        assertThat(productService.listAllProducts()).isEmpty();
        verify(productRepository).findAllByOrderByCreatedAtDesc();
        verify(reviewRepository, never()).findRatingStatsByProductIds(any());
    }

    @Test
    void fetchingAnUnknownAdminProductIdIsNotFound() {
        when(productRepository.findById(any())).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(java.util.UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }
}
