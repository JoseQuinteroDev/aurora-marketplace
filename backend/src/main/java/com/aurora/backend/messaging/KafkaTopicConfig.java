package com.aurora.backend.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the domain-event topics. Spring Kafka's admin client creates any
 * missing topic on startup. Single partition / single replica is appropriate
 * for the local single-broker development cluster.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic orderCreatedTopic() {
        return TopicBuilder.name(AuroraTopics.ORDER_CREATED).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic paymentConfirmedTopic() {
        return TopicBuilder.name(AuroraTopics.PAYMENT_CONFIRMED).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic paymentFailedTopic() {
        return TopicBuilder.name(AuroraTopics.PAYMENT_FAILED).partitions(1).replicas(1).build();
    }
}
