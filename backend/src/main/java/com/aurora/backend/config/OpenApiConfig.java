package com.aurora.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 definition for the interactive docs (Swagger UI at {@code /swagger-ui.html},
 * machine-readable spec at {@code /v3/api-docs}). Declares a global HTTP Bearer (JWT) security
 * scheme so the "Authorize" button in Swagger UI lets a reviewer paste an access token and call
 * the protected endpoints directly. springdoc generates the operations from the controllers and
 * the Bean Validation annotations already on the request DTOs.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI auroraOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Aurora Marketplace API")
                        .version("v1")
                        .description("""
                                Event-driven e-commerce core. Stateless JWT auth (Bearer); authorities are
                                reloaded from the database, not trusted from the token claim. Errors use a
                                consistent ErrorResponse envelope (status, code, message, path). Send the
                                Bearer access token via the Authorize button to call protected endpoints.""")
                        .contact(new Contact().name("Aurora Marketplace"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                // Global requirement; public endpoints (auth, public catalog reads) simply ignore it.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .name(BEARER_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
