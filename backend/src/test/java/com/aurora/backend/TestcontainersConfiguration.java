package com.aurora.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Provides a throwaway PostgreSQL container for {@code @SpringBootTest}, wired to the
 * datasource via {@code @ServiceConnection}. This lets the full Spring context load in
 * CI (and anywhere with Docker) without a pre-provisioned database — Flyway migrates the
 * fresh container and {@code ddl-auto: validate} checks the schema against it.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }
}
