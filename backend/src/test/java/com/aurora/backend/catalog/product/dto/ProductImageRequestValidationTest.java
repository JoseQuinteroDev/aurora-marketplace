package com.aurora.backend.catalog.product.dto;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that a product image URL must be an absolute http(s) URL (OWASP A03/A10 — input
 * hardening / SSRF prevention). Blocks {@code javascript:}/{@code data:}/{@code file:} scheme
 * abuse and relative URLs on stored, browser-rendered image links. Pure Bean Validation — no
 * Spring context.
 */
class ProductImageRequestValidationTest {

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

    private boolean urlRejected(String url) {
        Set<ConstraintViolation<ProductImageRequest>> violations =
                validator.validate(new ProductImageRequest(url, "alt", 0, true));
        return violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("url"));
    }

    @Test
    void acceptsAbsoluteHttpAndHttpsUrls() {
        assertThat(urlRejected("https://cdn.aurora.test/img/lamp.jpg")).isFalse();
        assertThat(urlRejected("http://cdn.aurora.test/img/lamp.jpg")).isFalse();
        assertThat(urlRejected("HTTPS://CDN.AURORA.TEST/x.png")).isFalse();   // scheme is case-insensitive
    }

    @Test
    void rejectsDangerousAndRelativeSchemes() {
        assertThat(urlRejected("javascript:alert(1)")).isTrue();
        assertThat(urlRejected("data:text/html;base64,PHNjcmlwdD4=")).isTrue();
        assertThat(urlRejected("file:///etc/passwd")).isTrue();
        assertThat(urlRejected("ftp://internal/host")).isTrue();
        assertThat(urlRejected("/relative/path.png")).isTrue();
        assertThat(urlRejected("//protocol-relative.test/x.png")).isTrue();
    }
}
