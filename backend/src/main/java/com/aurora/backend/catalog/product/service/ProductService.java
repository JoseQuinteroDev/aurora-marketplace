package com.aurora.backend.catalog.product.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.aurora.backend.catalog.brand.entity.Brand;
import com.aurora.backend.catalog.brand.service.BrandService;
import com.aurora.backend.catalog.category.entity.Category;
import com.aurora.backend.catalog.category.service.CategoryService;
import com.aurora.backend.catalog.common.SlugUtils;
import com.aurora.backend.catalog.product.dto.ProductImageRequest;
import com.aurora.backend.catalog.product.dto.ProductRequest;
import com.aurora.backend.catalog.product.dto.ProductResponse;
import com.aurora.backend.catalog.product.dto.ProductVariantRequest;
import com.aurora.backend.catalog.product.entity.Product;
import com.aurora.backend.catalog.product.entity.ProductImage;
import com.aurora.backend.catalog.product.entity.ProductVariant;
import com.aurora.backend.catalog.product.repository.ProductRepository;
import com.aurora.backend.catalog.product.repository.ProductVariantRepository;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.common.exception.NotFoundException;
import com.aurora.backend.inventory.service.InventoryService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final InventoryService inventoryService;

    public ProductService(
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            CategoryService categoryService,
            BrandService brandService,
            InventoryService inventoryService
    ) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.categoryService = categoryService;
        this.brandService = brandService;
        this.inventoryService = inventoryService;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listActiveProducts() {
        return productRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getActiveProductBySlug(String slug) {
        return productRepository.findBySlugAndActiveTrue(SlugUtils.from(slug))
                .map(ProductResponse::from)
                .orElseThrow(() -> new NotFoundException("Product", slug));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> searchActiveProducts(String query) {
        if (query == null || query.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SEARCH_QUERY_REQUIRED", "Search query is required.");
        }

        return productRepository.searchActive(query.trim()).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        String slug = SlugUtils.fromOptional(request.slug(), request.name());
        ensureProductSlugAvailable(slug);
        validateVariantRequests(request.variants());
        validateImageRequests(request.images());

        Category category = categoryService.getActiveCategory(request.categoryId());
        Brand brand = brandService.getActiveBrand(request.brandId());

        Product product = new Product(
                request.name().trim(),
                slug,
                normalizeNullableText(request.description()),
                normalizeNullableText(request.shortDescription()),
                request.basePrice(),
                activeOrDefault(request.active()),
                Boolean.TRUE.equals(request.featured()),
                category,
                brand
        );

        addVariants(product, request.variants());
        replaceImages(product, request.images());

        Product savedProduct = productRepository.saveAndFlush(product);
        initializeVariantInventory(savedProduct);
        return ProductResponse.from(savedProduct);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        Product product = findProduct(id);
        String slug = SlugUtils.fromOptional(request.slug(), request.name());

        if (productRepository.existsBySlugAndIdNot(slug, id)) {
            throw duplicateProductSlugException();
        }

        validateVariantRequests(request.variants());
        validateImageRequests(request.images());

        Category category = categoryService.getActiveCategory(request.categoryId());
        Brand brand = brandService.getActiveBrand(request.brandId());

        product.update(
                request.name().trim(),
                slug,
                normalizeNullableText(request.description()),
                normalizeNullableText(request.shortDescription()),
                request.basePrice(),
                activeOrDefault(request.active()),
                Boolean.TRUE.equals(request.featured()),
                category,
                brand
        );

        syncVariants(product, request.variants());

        if (request.images() != null) {
            replaceImages(product, request.images());
        }

        Product savedProduct = productRepository.saveAndFlush(product);
        initializeVariantInventory(savedProduct);
        return ProductResponse.from(savedProduct);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = findProduct(id);
        product.deactivate();
        product.getVariants().forEach(ProductVariant::deactivate);
    }

    private Product findProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
    }

    private void addVariants(Product product, List<ProductVariantRequest> variantRequests) {
        if (variantRequests == null) {
            return;
        }

        variantRequests.forEach(request -> product.addVariant(new ProductVariant(
                normalizeSku(request.sku()),
                request.name().trim(),
                request.priceOverride(),
                normalizeNullableText(request.attributesJson()),
                activeOrDefault(request.active())
        )));
    }

    private void syncVariants(Product product, List<ProductVariantRequest> variantRequests) {
        if (variantRequests == null) {
            return;
        }

        Map<UUID, ProductVariant> existingVariants = new HashMap<>();
        product.getVariants().forEach(variant -> existingVariants.put(variant.getId(), variant));

        Set<UUID> requestedExistingIds = new HashSet<>();

        for (ProductVariantRequest request : variantRequests) {
            String sku = normalizeSku(request.sku());

            if (request.id() == null) {
                ensureVariantSkuAvailable(sku);
                product.addVariant(new ProductVariant(
                        sku,
                        request.name().trim(),
                        request.priceOverride(),
                        normalizeNullableText(request.attributesJson()),
                        activeOrDefault(request.active())
                ));
                continue;
            }

            ProductVariant variant = existingVariants.get(request.id());

            if (variant == null) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "PRODUCT_VARIANT_NOT_IN_PRODUCT",
                        "Variant does not belong to this product."
                );
            }

            if (productVariantRepository.existsBySkuAndIdNot(sku, variant.getId())) {
                throw duplicateVariantSkuException();
            }

            variant.update(
                    sku,
                    request.name().trim(),
                    request.priceOverride(),
                    normalizeNullableText(request.attributesJson()),
                    activeOrDefault(request.active())
            );
            requestedExistingIds.add(variant.getId());
        }

        product.getVariants().stream()
                .filter(variant -> !requestedExistingIds.contains(variant.getId()))
                .filter(variant -> variant.getId() != null)
                .forEach(ProductVariant::deactivate);
    }

    private void replaceImages(Product product, List<ProductImageRequest> imageRequests) {
        product.clearImages();

        if (imageRequests == null) {
            return;
        }

        imageRequests.forEach(request -> product.addImage(new ProductImage(
                request.url().trim(),
                normalizeNullableText(request.altText()),
                request.position() == null ? 0 : request.position(),
                Boolean.TRUE.equals(request.mainImage())
        )));
    }

    private void initializeVariantInventory(Product product) {
        product.getVariants().forEach(inventoryService::initializeInventory);
    }

    private void ensureProductSlugAvailable(String slug) {
        if (productRepository.existsBySlug(slug)) {
            throw duplicateProductSlugException();
        }
    }

    private void ensureVariantSkuAvailable(String sku) {
        if (productVariantRepository.existsBySku(sku)) {
            throw duplicateVariantSkuException();
        }
    }

    private void validateVariantRequests(List<ProductVariantRequest> variantRequests) {
        if (variantRequests == null) {
            return;
        }

        Set<String> skus = new HashSet<>();

        for (ProductVariantRequest request : variantRequests) {
            String sku = normalizeSku(request.sku());
            if (!skus.add(sku)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "DUPLICATE_VARIANT_SKU_IN_REQUEST",
                        "Variant SKUs must be unique in the request."
                );
            }

            if (request.id() == null) {
                ensureVariantSkuAvailable(sku);
            }
        }
    }

    private void validateImageRequests(List<ProductImageRequest> imageRequests) {
        if (imageRequests == null) {
            return;
        }

        long mainImageCount = imageRequests.stream()
                .filter(image -> Boolean.TRUE.equals(image.mainImage()))
                .count();

        if (mainImageCount > 1) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "MULTIPLE_MAIN_PRODUCT_IMAGES",
                    "Only one product image can be marked as main."
            );
        }
    }

    private BusinessException duplicateProductSlugException() {
        return new BusinessException(HttpStatus.CONFLICT, "PRODUCT_SLUG_EXISTS", "Product slug already exists.");
    }

    private BusinessException duplicateVariantSkuException() {
        return new BusinessException(HttpStatus.CONFLICT, "VARIANT_SKU_EXISTS", "Variant SKU already exists.");
    }

    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private boolean activeOrDefault(Boolean active) {
        return active == null || active;
    }
}
