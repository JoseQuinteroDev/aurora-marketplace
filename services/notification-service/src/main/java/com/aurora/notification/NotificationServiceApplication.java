package com.aurora.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aurora Marketplace Notification Service.
 *
 * <p>A standalone microservice that subscribes to the platform's domain events
 * (order created, payment confirmed, payment failed) and reacts by sending
 * transactional emails. It owns its own data and shares nothing with the core
 * service except the Kafka topic contract.</p>
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
