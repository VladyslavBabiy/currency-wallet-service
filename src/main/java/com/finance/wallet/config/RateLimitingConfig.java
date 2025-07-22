package com.finance.wallet.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Configuration
@Component
@Slf4j
public class RateLimitingConfig {
    
    @Value("${wallet.rate-limiting.capacity:20}")
    private int capacity;
    
    @Value("${wallet.rate-limiting.refill-rate:20}")
    private int refillRate;
    
    @Value("${wallet.rate-limiting.refill-period:1m}")
    private Duration refillPeriod;
    
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    public Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, this::newBucket);
    }
    
    private Bucket newBucket(String key) {
        Bandwidth bandwidth = Bandwidth.simple(capacity, refillPeriod);
        return Bucket.builder()
                .addLimit(bandwidth)
                .build();
    }
} 