package com.aurora.notification.config;

import com.aurora.notification.listener.NonRetryableEventException;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Consumer-side resilience.
 *
 * <p>Transient failures (e.g. the SMTP server is briefly down) are retried with
 * exponential backoff. If retries are exhausted — or the record is a poison
 * message that can never deserialize — it is routed to a per-topic
 * <strong>dead-letter topic</strong> ({@code <topic>.DLT}) instead of blocking
 * the partition or being silently dropped. The DLT preserves the bad record for
 * inspection and replay.</p>
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    /** A String producer used only to forward failed records to dead-letter topics. */
    @Bean
    public ProducerFactory<String, String> dltProducerFactory(KafkaProperties kafkaProperties) {
        var props = kafkaProperties.buildProducerProperties(null);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> dltKafkaTemplate(ProducerFactory<String, String> dltProducerFactory) {
        return new KafkaTemplate<>(dltProducerFactory);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> dltKafkaTemplate,
            @Value("${app.consumer.retry.attempts:3}") int maxRetries,
            @Value("${app.consumer.retry.initial-interval-ms:1000}") long initialInterval,
            @Value("${app.consumer.retry.multiplier:2.0}") double multiplier,
            @Value("${app.consumer.retry.max-interval-ms:10000}") long maxInterval
    ) {
        // Forward exhausted/poison records to "<original-topic>.DLT", same partition.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltKafkaTemplate,
                (record, exception) -> {
                    log.error("Routing record from {} to DLT after failure: {}",
                            record.topic(), exception.getMessage());
                    return new TopicPartition(record.topic() + ".DLT", record.partition());
                });

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialInterval);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(maxInterval);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // Poison messages can never succeed — skip the retries and dead-letter now.
        handler.addNotRetryableExceptions(NonRetryableEventException.class);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            org.springframework.kafka.core.ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
