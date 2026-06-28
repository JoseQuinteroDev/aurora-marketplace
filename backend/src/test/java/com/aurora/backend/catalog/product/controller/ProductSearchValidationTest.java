package com.aurora.backend.catalog.product.controller;

import java.util.List;

import com.aurora.backend.catalog.product.service.ProductService;
import com.aurora.backend.config.SecurityConfig;
import com.aurora.backend.security.jwt.JwtAuthenticationFilter;
import com.aurora.backend.security.jwt.JwtService;
import com.aurora.backend.security.token.TokenDenylistService;
import com.aurora.backend.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Input-validation tests for the public product search endpoint (OWASP A03 — input
 * hardening / resource-exhaustion hygiene): an over-long {@code q} request param is
 * rejected with a clean {@code 400 VALIDATION_ERROR} via
 * {@link com.aurora.backend.common.exception.GlobalExceptionHandler}, and the service
 * is never reached. The cap is enforced by {@code @Validated} on the controller, so
 * this fails if that annotation (or the {@code @Size} on {@code q}) is removed.
 */
@WebMvcTest(controllers = ProductController.class)
@Import({SecurityConfig.class, ProductSearchValidationTest.TestSecurityBeans.class})
class ProductSearchValidationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private ProductService productService;
    @MockitoBean private UserRepository userRepository;   // backs SecurityConfig#userDetailsService

    static class TestSecurityBeans {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(
                    mock(JwtService.class),
                    mock(UserDetailsService.class),
                    mock(TokenDenylistService.class));
        }
    }

    @Test
    void searchWithAnOverLongQueryIs400AndDoesNotCallTheService() throws Exception {
        String tooLong = "x".repeat(101);

        mvc.perform(get("/api/products/search").param("q", tooLong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(productService, never()).searchActiveProducts(anyString());
    }

    @Test
    void searchWithAQueryAtTheCapReachesTheService() throws Exception {
        when(productService.searchActiveProducts(any())).thenReturn(List.of());

        mvc.perform(get("/api/products/search").param("q", "x".repeat(100)))
                .andExpect(status().isOk());

        verify(productService).searchActiveProducts(anyString());
    }
}
