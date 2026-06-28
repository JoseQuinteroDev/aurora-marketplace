package com.aurora.backend.config;

import com.aurora.backend.catalog.product.controller.AdminProductController;
import com.aurora.backend.catalog.product.service.ProductService;
import com.aurora.backend.security.jwt.JwtService;
import com.aurora.backend.security.token.TokenDenylistService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Method-level authorization (defense-in-depth) tests for the admin surface (OWASP A01).
 *
 * <p>{@link AdminAuthorizationTest} locks the URL rule ({@code /api/admin/** → hasRole('ADMIN')}),
 * which denies at Spring Security's filter chain. This slice instead pins the belt-and-suspenders
 * control: {@code @EnableMethodSecurity} on {@link SecurityConfig} plus the class-level
 * {@code @PreAuthorize("hasRole('ADMIN')")} on each admin controller. To prove the <em>method</em>
 * layer (independently of the URL rule), the test runs against a real admin controller under a
 * deliberately <strong>permit-all</strong> URL policy: any 403 can then only come from
 * {@code @PreAuthorize}. The denial reaches the dispatcher as an {@code AccessDeniedException},
 * which {@code GlobalExceptionHandler} maps to a clean 403. Runs as a slice test (no DB, no Docker).
 */
@WebMvcTest(controllers = AdminProductController.class)
@Import(AdminMethodSecurityTest.MethodSecurityOnlyConfig.class)
class AdminMethodSecurityTest {

    private static final String ANY_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductService productService;

    // The real JwtAuthenticationFilter (@Component) is registered by the slice; mock its
    // collaborators so the context loads. With no Bearer header it is a no-op passthrough,
    // leaving the test-provided authentication to drive method-security authorization.
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private TokenDenylistService tokenDenylistService;

    @Test
    void customerIsDeniedOnAnAdminControllerByMethodSecurityAlone() throws Exception {
        // URL authorization is permit-all here, so a 403 can ONLY come from the class-level
        // @PreAuthorize("hasRole('ADMIN')") under @EnableMethodSecurity — the defense-in-depth
        // layer under test. This fails (200) if the annotation or method security is removed.
        mvc.perform(delete("/api/admin/products/{id}", ANY_ID).with(user("c").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                // Pins the 403 body contract: the method-security advice uses the same FORBIDDEN
                // code as the URL-level filter-chain handler, so both 403 sources are uniform.
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminIsAllowedByMethodSecurity() throws Exception {
        // ProductService is mocked, so a 200 means @PreAuthorize let the call reach the handler.
        mvc.perform(delete("/api/admin/products/{id}", ANY_ID).with(user("a").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void securityConfigEnablesMethodSecurity() {
        // Pins the real wiring: every admin @PreAuthorize is inert without @EnableMethodSecurity.
        assertThat(SecurityConfig.class.isAnnotationPresent(EnableMethodSecurity.class))
                .as("@EnableMethodSecurity must stay on SecurityConfig for admin @PreAuthorize to be enforced")
                .isTrue();
    }

    /**
     * Opens every URL so the only authorization gate left is method security ({@code @PreAuthorize}),
     * isolating it from the production {@code /api/admin/**} URL rule (covered by
     * {@link AdminAuthorizationTest}). Overrides the slice's default security chain.
     */
    @EnableMethodSecurity
    static class MethodSecurityOnlyConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }
}
