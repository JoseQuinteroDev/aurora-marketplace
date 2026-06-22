package com.aurora.backend.security.jwt;

import com.aurora.backend.security.token.TokenDenylistService;

import java.io.IOException;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TokenDenylistService tokenDenylist;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            TokenDenylistService tokenDenylist
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.tokenDenylist = tokenDenylist;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length());

        try {
            authenticateRequest(request, token);
        } catch (JwtException | IllegalArgumentException exception) {
            // Rejected token (expired / malformed / forged): a security signal worth
            // recording, but never echo the token itself.
            log.warn("Rejected JWT on {} {}: {}", request.getMethod(), request.getRequestURI(),
                    exception.getClass().getSimpleName());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateRequest(HttpServletRequest request, String token) {
        String email = jwtService.extractSubject(token);

        if (!StringUtils.hasText(email) || SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        if (!jwtService.isTokenValid(token, userDetails)) {
            return;
        }

        // Server-side revocation: a logged-out (or otherwise revoked) token is rejected
        // even though its signature and expiry still check out.
        if (tokenDenylist.isRevoked(jwtService.extractJti(token))) {
            log.warn("Rejected revoked JWT on {} {}", request.getMethod(), request.getRequestURI());
            return;
        }

        // LAB 02 — privilege escalation via the JWT role claim (OWASP A01/A07).
        // main builds the principal with userDetails.getAuthorities(), i.e. the role
        // loaded from the DATABASE — the token's "role" claim is non-authoritative.
        // Here we instead trust the claim carried in the token, so anyone who can mint
        // a token saying "role":"ADMIN" (e.g. with the known dev secret, or simply by
        // keeping a stale token after a DB demotion) becomes an admin.
        // See docs/appsec/labs/02-jwt-trusting-the-role-claim.md
        var authoritiesFromToken = java.util.List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_" + jwtService.extractRole(token)));
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                authoritiesFromToken
        );
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }
}
