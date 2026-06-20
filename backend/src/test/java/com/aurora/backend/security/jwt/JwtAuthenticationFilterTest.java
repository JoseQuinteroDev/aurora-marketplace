package com.aurora.backend.security.jwt;

import com.aurora.backend.security.token.TokenDenylistService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security unit tests for {@link JwtAuthenticationFilter}.
 *
 * <p>Verifies the request-time authentication behavior without a Spring context:
 * the filter authenticates only on a valid token, fails closed on a bad one, and
 * — critically — derives the principal's authorities from the
 * {@link UserDetailsService} (the database), never from the token itself. That is
 * the control that makes a tampered {@code role} claim worthless (OWASP A01).
 */
class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
    private final TokenDenylistService tokenDenylist = mock(TokenDenylistService.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtService, userDetailsService, tokenDenylist);

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private UserDetails dbUser(String email, String authority) {
        return org.springframework.security.core.userdetails.User
                .withUsername(email)
                .password("hash")
                .authorities(authority)
                .build();
    }

    @Test
    void withoutAnAuthorizationHeaderItStaysAnonymousAndContinues() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void withAValidTokenItAuthenticatesUsingDatabaseAuthorities() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer good.token.value");
        when(jwtService.extractSubject("good.token.value")).thenReturn("ada@aurora.test");
        UserDetails details = dbUser("ada@aurora.test", "ROLE_ADMIN");
        when(userDetailsService.loadUserByUsername("ada@aurora.test")).thenReturn(details);
        when(jwtService.isTokenValid("good.token.value", details)).thenReturn(true);

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("ada@aurora.test");
        // Authority comes from the loaded user (DB), not from any token claim.
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void withAnInvalidTokenItFailsClosedAndStaysAnonymous() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer forged.token.value");
        when(jwtService.extractSubject(anyString()))
                .thenThrow(new JwtException("bad signature"));

        filter.doFilter(request, response, chain);

        // The request is not authenticated, but the chain still proceeds
        // (downstream authorization rules will return 401/403).
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }
}
