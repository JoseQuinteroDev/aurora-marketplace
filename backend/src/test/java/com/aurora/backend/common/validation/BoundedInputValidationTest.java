package com.aurora.backend.common.validation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import com.aurora.backend.cart.dto.AddCartItemRequest;
import com.aurora.backend.cart.dto.UpdateCartItemRequest;
import com.aurora.backend.catalog.product.dto.ProductImageRequest;
import com.aurora.backend.catalog.product.dto.ProductRequest;
import com.aurora.backend.catalog.product.dto.ProductVariantRequest;
import com.aurora.backend.review.dto.ReviewRequest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bounds the size of free-text and numeric inputs so a single request cannot persist
 * multi-MB payloads or absurd quantities (OWASP A03/A08 — input hardening, resource
 * exhaustion). Each oversized field must raise a constraint violation; values at the
 * cap must pass. Pure Bean Validation — no Spring context, no Docker.
 */
class BoundedInputValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private boolean fieldRejected(Object bean, String field) {
        return validator.validate(bean).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(field));
    }

    private String repeat(int length) {
        return "x".repeat(length);
    }

    @Test
    void reviewCommentIsCappedAt2000Characters() {
        assertThat(fieldRejected(new ReviewRequest(5, "ok", repeat(2000)), "comment")).isFalse();
        assertThat(fieldRejected(new ReviewRequest(5, "ok", repeat(2001)), "comment")).isTrue();
    }

    @Test
    void productDescriptionIsCappedAt5000Characters() {
        assertThat(fieldRejected(productWithDescription(repeat(5000)), "description")).isFalse();
        assertThat(fieldRejected(productWithDescription(repeat(5001)), "description")).isTrue();
    }

    @Test
    void variantAttributesJsonIsCappedAt2000Characters() {
        assertThat(fieldRejected(variantWithAttributes(repeat(2000)), "attributesJson")).isFalse();
        assertThat(fieldRejected(variantWithAttributes(repeat(2001)), "attributesJson")).isTrue();
    }

    @Test
    void addCartItemQuantityIsCappedAt1000() {
        assertThat(fieldRejected(new AddCartItemRequest(UUID.randomUUID(), 1000), "quantity")).isFalse();
        assertThat(fieldRejected(new AddCartItemRequest(UUID.randomUUID(), 1001), "quantity")).isTrue();
    }

    @Test
    void updateCartItemQuantityIsCappedAt1000() {
        assertThat(fieldRejected(new UpdateCartItemRequest(1000), "quantity")).isFalse();
        assertThat(fieldRejected(new UpdateCartItemRequest(1001), "quantity")).isTrue();
    }

    @Test
    void productVariantsListIsCappedAt100() {
        assertThat(fieldRejected(productWithVariants(100), "variants")).isFalse();
        assertThat(fieldRejected(productWithVariants(101), "variants")).isTrue();
    }

    @Test
    void productImagesListIsCappedAt50() {
        assertThat(fieldRejected(productWithImages(50), "images")).isFalse();
        assertThat(fieldRejected(productWithImages(51), "images")).isTrue();
    }

    @Test
    void productBasePriceDigitsAreCapped() {
        assertThat(fieldRejected(productWithBasePrice(new BigDecimal("9999999999.99")), "basePrice")).isFalse();
        assertThat(fieldRejected(productWithBasePrice(new BigDecimal("12345678901.00")), "basePrice")).isTrue();
    }

    @Test
    void variantPriceOverrideDigitsAreCapped() {
        assertThat(fieldRejected(variantWithPriceOverride(new BigDecimal("9999999999.99")), "priceOverride")).isFalse();
        assertThat(fieldRejected(variantWithPriceOverride(new BigDecimal("12345678901.00")), "priceOverride")).isTrue();
    }

    private ProductRequest productWithDescription(String description) {
        return new ProductRequest(
                "Lamp", "lamp", description, "short", new BigDecimal("9.99"),
                true, false, UUID.randomUUID(), UUID.randomUUID(), null, null);
    }

    private ProductVariantRequest variantWithAttributes(String attributesJson) {
        return new ProductVariantRequest(
                null, "SKU-1", "Variant", new BigDecimal("9.99"), attributesJson, true);
    }

    private ProductRequest productWithVariants(int count) {
        List<ProductVariantRequest> variants = IntStream.range(0, count)
                .mapToObj(i -> new ProductVariantRequest(null, "SKU-" + i, "Variant " + i, null, null, true))
                .toList();
        return new ProductRequest(
                "Lamp", "lamp", "desc", "short", new BigDecimal("9.99"),
                true, false, UUID.randomUUID(), UUID.randomUUID(), variants, null);
    }

    private ProductRequest productWithImages(int count) {
        List<ProductImageRequest> images = IntStream.range(0, count)
                .mapToObj(i -> new ProductImageRequest("https://cdn.example.com/" + i + ".png", null, i, false))
                .toList();
        return new ProductRequest(
                "Lamp", "lamp", "desc", "short", new BigDecimal("9.99"),
                true, false, UUID.randomUUID(), UUID.randomUUID(), null, images);
    }

    private ProductRequest productWithBasePrice(BigDecimal basePrice) {
        return new ProductRequest(
                "Lamp", "lamp", "desc", "short", basePrice,
                true, false, UUID.randomUUID(), UUID.randomUUID(), null, null);
    }

    private ProductVariantRequest variantWithPriceOverride(BigDecimal priceOverride) {
        return new ProductVariantRequest(null, "SKU-1", "Variant", priceOverride, null, true);
    }
}
