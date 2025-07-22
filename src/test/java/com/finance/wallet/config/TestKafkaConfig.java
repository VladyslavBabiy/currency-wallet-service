package com.finance.wallet.config;

import com.finance.wallet.service.KafkaConsumerService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestKafkaConfig {
    
    @Bean
    @Primary
    public KafkaConsumerService kafkaConsumerService() {
        // Return a mock to completely disable Kafka consumer during tests
        return mock(KafkaConsumerService.class);
    }
} 