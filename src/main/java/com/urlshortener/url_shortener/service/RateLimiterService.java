package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.exception.RateLimitExceededException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final RedissonClient redissonClient;
    private final MeterRegistry meterRegistry;

    private Counter rateLimitBlockedCounter;

    @Value("${app.rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Value("${app.rate-limit.max-requests:20}")
    private int maxRequests;

    @PostConstruct
    public void init() {
        this.rateLimitBlockedCounter = meterRegistry.counter("url.rate_limit.blocked");
    }

    /**
     * Distributed Sliding Window Rate Limiter.
     * Uses Redisson's RRateLimiter for high-precision atomic limiting.
     */
    public void checkRateLimit(String key) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter("rate_limit:" + key);

        // Initialize configuration (Overwrites only if not already set or changed)
        // RateType.OVERALL ensures the limit is shared across all app instances
        rateLimiter.trySetRate(RateType.OVERALL, maxRequests, windowSeconds, RateIntervalUnit.SECONDS);

        // Try to acquire 1 permit
        if (!rateLimiter.tryAcquire(1)) {
            rateLimitBlockedCounter.increment();
            throw new RateLimitExceededException("Rate limit exceeded for: " + key);
        }
    }
}