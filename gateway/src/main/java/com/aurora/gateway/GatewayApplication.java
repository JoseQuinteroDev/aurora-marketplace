package com.aurora.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aurora Marketplace API Gateway.
 *
 * <p>Single entry point for the platform. Routes public traffic to the core
 * commerce service and exposes a unified health surface for the event-driven
 * microservice landscape (core backend + notification-service + Kafka).</p>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
