package com.finance.wallet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FxRateService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${wallet.fx.api.url}")
    private String fxApiUrl;
    
    @Value("${wallet.fx.cache-ttl}")
    private Duration cacheTtl;
    
    private static final String CACHE_KEY_PREFIX = "fx_rate:";
    
    public BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return BigDecimal.ONE;
        }
        
        String cacheKey = CACHE_KEY_PREFIX + fromCurrency + "_" + toCurrency;
        
        Optional<BigDecimal> cachedRate = getCachedRate(cacheKey);
        if (cachedRate.isPresent()) {
            log.debug("Using cached FX rate for {}/{}: {}", fromCurrency, toCurrency, cachedRate.get());
            return cachedRate.get();
        }
        
        try {
            BigDecimal rate = fetchRateFromApi(fromCurrency, toCurrency);
            cacheRate(cacheKey, rate);
            log.info("Fetched and cached FX rate for {}/{}: {}", fromCurrency, toCurrency, rate);
            return rate;
        } catch (Exception e) {
            log.error("Failed to fetch FX rate for {}/{}", fromCurrency, toCurrency, e);
            throw new RuntimeException("FX rate service unavailable", e);
        }
    }
    
    private Optional<BigDecimal> getCachedRate(String cacheKey) {
        try {
            String cachedValue = redisTemplate.opsForValue().get(cacheKey);
            if (cachedValue != null) {
                return Optional.of(new BigDecimal(cachedValue));
            }
        } catch (Exception e) {
            log.warn("Failed to get cached FX rate for key: {}", cacheKey, e);
        }
        return Optional.empty();
    }
    
    private void cacheRate(String cacheKey, BigDecimal rate) {
        try {
            redisTemplate.opsForValue().set(cacheKey, rate.toString(), cacheTtl);
        } catch (Exception e) {
            log.warn("Failed to cache FX rate for key: {}", cacheKey, e);
        }
    }
    
    private BigDecimal fetchRateFromApi(String fromCurrency, String toCurrency) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = fxApiUrl + fromCurrency;
            
            String response = restTemplate.getForObject(url, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);
            
            if (jsonNode.has("rates") && jsonNode.get("rates").has(toCurrency)) {
                double rate = jsonNode.get("rates").get(toCurrency).asDouble();
                return BigDecimal.valueOf(rate).setScale(6, RoundingMode.HALF_UP);
            } else {
                return getMockExchangeRate(fromCurrency, toCurrency);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch real FX rate, using mock rate", e);
            return getMockExchangeRate(fromCurrency, toCurrency);
        }
    }


    //TODO Mock exchange rates for demo purposes for production should implement a real API call like https://www.exchangerate-api.com/
    private BigDecimal getMockExchangeRate(String fromCurrency, String toCurrency) {
        if ("USD".equals(fromCurrency) && "TRY".equals(toCurrency)) {
            return BigDecimal.valueOf(33.25); // 1 USD = 33.25 TRY
        } else if ("TRY".equals(fromCurrency) && "USD".equals(toCurrency)) {
            return BigDecimal.valueOf(0.030075); // 1 TRY = 0.030075 USD
        }
        
        return BigDecimal.ONE;
    }
} 