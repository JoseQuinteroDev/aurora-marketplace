package com.aurora.backend.config;

import java.util.List;

import com.aurora.backend.order.controller.AdminOrderController;
import com.aurora.backend.order.service.OrderService;
import com.aurora.backend.security.CurrentUserService;
import com.aurora.backend.security.jwt.JwtAuthenticationFilter;
import com.aurora.backend.security.jwt.JwtService;
import com.aurora.backend.security.token.TokenDenylistService;
import com.aurora.backend.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer authorization test for the real security filter chain (OWASP A01).
 *
 * <p>Unlike the service-layer unit tests, this drives an actual admin endpoint
 * through {@link SecurityConfig} and asserts the RBAC wiring: anonymous → 401,
 * a {@code ROLE_CUSTOMER} → 403, a {@code ROLE_ADMIN} → 200. Runs as a slice test
 * (no database, no Docker): the JWT filter is given mocked collaborators and, with
 * no {@code Authorization} header, simply passes the request through so the
 * test-provided authentication drives the {@code /api/admin/**} rule.
 */
@WebMvcTest(controllers = AdminOrderController.class)
@Import({SecurityConfig.class, AdminAuthorizationTest.TestSecurityBeans.class})
class AdminAuthorizationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mvc;

    @MockitoBean private OrderService orderService;
    @MockitoBean private CurrentUserService currentUserService;
    @MockitoBean private UserRepository userRepository;   // backs SecurityConfig#userDetailsService

    static class TestSecurityBeans {
        // A real filter with mocked dependencies: with no Bearer header it is a no-op
        // passthrough, leaving the test's SecurityContext intact for the authz rules.
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(
                    mock(JwtService.class),
                    mock(UserDetailsService.class),
                    mock(TokenDenylistService.class));
        }
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerIsForbidden() throws Exception {
        mvc.perform(get("/api/admin/orders").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminIsAllowed() throws Exception {
        when(orderService.listAllOrders()).thenReturn(List.of());

        mvc.perform(get("/api/admin/orders").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
