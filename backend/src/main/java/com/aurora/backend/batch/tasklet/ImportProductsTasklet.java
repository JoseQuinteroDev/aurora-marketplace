package com.aurora.backend.batch.tasklet;

import java.nio.file.Path;

import com.aurora.backend.batch.dto.ProductImportLine;
import com.aurora.backend.batch.support.BatchFileReader;
import com.aurora.backend.catalog.brand.entity.Brand;
import com.aurora.backend.catalog.brand.repository.BrandRepository;
import com.aurora.backend.catalog.category.entity.Category;
import com.aurora.backend.catalog.category.repository.CategoryRepository;
import com.aurora.backend.catalog.common.SlugUtils;
import com.aurora.backend.catalog.product.entity.Product;
import com.aurora.backend.catalog.product.entity.ProductVariant;
import com.aurora.backend.catalog.product.repository.ProductRepository;
import com.aurora.backend.catalog.product.repository.ProductVariantRepository;
import com.aurora.backend.inventory.service.InventoryService;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ImportProductsTasklet implements Tasklet {

    private final BatchFileReader batchFileReader;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryService inventoryService;
    private final String importProductsFile;

    public ImportProductsTasklet(
            BatchFileReader batchFileReader,
            CategoryRepository categoryRepository,
            BrandRepository brandRepository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            InventoryService inventoryService,
            @Value("${app.batch.files.import-products}") String importProductsFile
    ) {
        this.batchFileReader = batchFileReader;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.inventoryService = inventoryService;
        this.importProductsFile = importProductsFile;
    }

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        for (ProductImportLine line : batchFileReader.readProductImportLines(Path.of(importProductsFile))) {
            importLine(line);
        }

        return RepeatStatus.FINISHED;
    }

    private void importLine(ProductImportLine line) {
        Category category = categoryRepository.findBySlug(SlugUtils.from(line.categorySlug()))
                .orElseGet(() -> categoryRepository.save(new Category(
                        line.categoryName(),
                        SlugUtils.from(line.categorySlug()),
                        true
                )));

        Brand brand = brandRepository.findBySlug(SlugUtils.from(line.brandSlug()))
                .orElseGet(() -> brandRepository.save(new Brand(
                        line.brandName(),
                        SlugUtils.from(line.brandSlug()),
                        true
                )));

        String productSlug = SlugUtils.fromOptional(line.slug(), line.name());
        Product product = productRepository.findBySlug(productSlug)
                .orElseGet(() -> productRepository.save(new Product(
                        line.name(),
                        productSlug,
                        line.description(),
                        line.shortDescription(),
                        line.basePrice(),
                        line.active(),
                        line.featured(),
                        category,
                        brand
                )));

        product.update(
                line.name(),
                productSlug,
                line.description(),
                line.shortDescription(),
                line.basePrice(),
                line.active(),
                line.featured(),
                category,
                brand
        );

        ProductVariant variant = productVariantRepository.findBySku(line.sku())
                .orElseGet(() -> {
                    ProductVariant newVariant = new ProductVariant(
                            line.sku(),
                            line.variantName(),
                            line.priceOverride(),
                            line.attributesJson(),
                            line.active()
                    );
                    product.addVariant(newVariant);
                    return newVariant;
                });

        if (!variant.getProduct().getId().equals(product.getId())) {
            throw new IllegalStateException("SKU " + line.sku() + " already belongs to another product.");
        }

        variant.update(
                line.sku(),
                line.variantName(),
                line.priceOverride(),
                line.attributesJson(),
                line.active()
        );

        Product savedProduct = productRepository.saveAndFlush(product);
        savedProduct.getVariants().forEach(inventoryService::initializeInventory);
    }
}
