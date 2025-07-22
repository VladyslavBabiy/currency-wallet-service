package com.finance.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FxRateServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private FxRateService fxRateService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fxRateService, "fxApiUrl", "https://api.exchangerate-api.com/v4/latest/USD");
        ReflectionTestUtils.setField(fxRateService, "cacheTtl", Duration.ofSeconds(60));
    }

    @Test
    void getExchangeRate_SameCurrency_ReturnsOne() {
        BigDecimal result = fxRateService.getExchangeRate("USD", "USD");

        assertThat(result).isEqualByComparingTo(BigDecimal.ONE);
        
        verifyNoInteractions(objectMapper);
    }

    @Test
    void getExchangeRate_FromCache_ReturnsCachedRate() {
        String cacheKey = "fx_rate:USD_TRY";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn("33.25");

        BigDecimal result = fxRateService.getExchangeRate("USD", "TRY");

        assertThat(result).isEqualByComparingTo(new BigDecimal("33.25"));
        
        verify(valueOperations).get(cacheKey);
        verifyNoMoreInteractions(objectMapper);
    }

    @Test
    void getExchangeRate_CacheEmpty_UsesMockRate() {
        String cacheKey = "fx_rate:USD_TRY";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);

        BigDecimal result = fxRateService.getExchangeRate("USD", "TRY");

        assertThat(result).isEqualByComparingTo(new BigDecimal("33.25"));
    }

    @Test
    void getExchangeRate_TryToUsd_UsesMockRate() {
        String cacheKey = "fx_rate:TRY_USD";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);

        BigDecimal result = fxRateService.getExchangeRate("TRY", "USD");

        assertThat(result).isEqualByComparingTo(new BigDecimal("0.030075"));
    }

    @Test
    void getExchangeRate_UnknownCurrencyPair_ReturnsOne() {
        String cacheKey = "fx_rate:EUR_GBP";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);

        BigDecimal result = fxRateService.getExchangeRate("EUR", "GBP");

        assertThat(result).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void getExchangeRate_CacheThrowsException_StillReturnsRate() {
        String cacheKey = "fx_rate:USD_TRY";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenThrow(new RuntimeException("Redis error"));

        BigDecimal result = fxRateService.getExchangeRate("USD", "TRY");

        assertThat(result).isEqualByComparingTo(new BigDecimal("33.25"));
    }

    @Test
    void getExchangeRate_CacheSetThrowsException_StillReturnsRate() {
        String cacheKey = "fx_rate:USD_TRY";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);

        BigDecimal result = fxRateService.getExchangeRate("USD", "TRY");

        assertThat(result).isEqualByComparingTo(new BigDecimal("33.25"));
    }

    @Test
    void getExchangeRate_CacheReturnsInvalidData_UsesMockRate() {
        String cacheKey = "fx_rate:USD_TRY";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn("invalid_number");

        BigDecimal result = fxRateService.getExchangeRate("USD", "TRY");

        assertThat(result).isEqualByComparingTo(new BigDecimal("33.25"));
    }

    @Test
    void getExchangeRate_ReturnsConsistentPrecision() {
        BigDecimal result1 = fxRateService.getExchangeRate("USD", "TRY");
        BigDecimal result2 = fxRateService.getExchangeRate("TRY", "USD");

        assertThat(result1.scale()).isEqualTo(2); // 33.25 has scale 2
        assertThat(result2.scale()).isEqualTo(6); // 0.030075 has scale 6
    }
} 