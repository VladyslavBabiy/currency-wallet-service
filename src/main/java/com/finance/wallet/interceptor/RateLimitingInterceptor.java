package com.finance.wallet.interceptor;

import com.finance.wallet.config.RateLimitingConfig;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingInterceptor implements HandlerInterceptor {
    
    private final RateLimitingConfig rateLimitingConfig;
    
    @Value("${wallet.rate-limiting.enabled:true}")
    private boolean rateLimitingEnabled;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        
        if (!rateLimitingEnabled) {
            return true;
        }
        
        // Only apply rate limiting to write operations (POST, PUT, DELETE)
        String method = request.getMethod();
        if (!"POST".equalsIgnoreCase(method) && 
            !"PUT".equalsIgnoreCase(method) && 
            !"DELETE".equalsIgnoreCase(method)) {
            return true;
        }
        
        String clientIp = getClientIP(request);
        String bucketKey = "rate_limit:" + clientIp;
        
        Bucket bucket = rateLimitingConfig.resolveBucket(bucketKey);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        
        if (probe.isConsumed()) {
            // Add rate limit headers
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            return true;
        } else {
            // Rate limit exceeded
            log.warn("Rate limit exceeded for IP: {} on endpoint: {}", clientIp, request.getRequestURI());
            
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", 
                    String.valueOf(probe.getNanosToWaitForRefill() / 1_000_000_000));
            response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
            
            return false;
        }
    }
    
    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
} 