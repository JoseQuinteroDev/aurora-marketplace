package com.aurora.backend.config;

import java.io.IOException;

import com.aurora.backend.common.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, exception) -> writeErrorResponse(
                                response,
                                objectMapper,
                                HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "Authentication is required.",
                                request
                        ))
                        .accessDeniedHandler((request, response, exception) -> writeErrorResponse(
                                response,
                                objectMapper,
                                HttpStatus.FORBIDDEN,
                                "FORBIDDEN",
                                "Access is denied.",
                                request
                        ))
                );

        return http.build();
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = ErrorResponse.of(status, code, message, request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
