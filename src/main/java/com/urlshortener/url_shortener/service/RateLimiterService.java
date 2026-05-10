package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.exception.RateLimitExceededException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    private Counter rateLimitBlockedCounter;

    // Bucket capacity — max requests allowed in the queue
    @Value("${app.rate-limit.max-requests:20}")
    private int bucketCapacity;

    // Leak rate — how many requests drain per second
    @Value("${app.rate-limit.leak-rate:1}")
    private int leakRatePerSecond;

    @PostConstruct
    public void init() {
        this.rateLimitBlockedCounter = meterRegistry.counter("url.rate_limit.blocked");
    }

    /**
     * Leaky Bucket Rate Limiter.
     *
     * Concept:
     *   - Requests fill a bucket of fixed capacity.
     *   - Bucket leaks (drains) at a constant rate regardless of incoming traffic.
     *   - If bucket is full, request is rejected — no bursting allowed.
     *
     * This ensures a SMOOTH, CONSTANT outflow — unlike token bucket which allows bursting.
     *
     * Redis keys per user:
     *   - rate_limit:{key}:count     → current water level (requests in bucket)
     *   - rate_limit:{key}:last_leak → timestamp of last leak calculation (epoch seconds)
     */
    public void checkRateLimit(String key) {
        String countKey = "rate_limit:" + key + ":count";
        String lastLeakKey = "rate_limit:" + key + ":last_leak";

        // Lua script for atomicity — no race conditions across instances
        String luaScript = """
                local countKey = KEYS[1]
                local lastLeakKey = KEYS[2]
                local now = tonumber(ARGV[1])
                local capacity = tonumber(ARGV[2])
                local leakRate = tonumber(ARGV[3])
                                
                -- Get current bucket state
                local count = tonumber(redis.call('GET', countKey) or 0)
                local lastLeak = tonumber(redis.call('GET', lastLeakKey) or now)
                                
                -- Calculate how much has leaked since last check
                local elapsed = now - lastLeak
                local leaked = math.floor(elapsed * leakRate)
                                
                -- Drain the bucket
                if leaked > 0 then
                    count = math.max(0, count - leaked)
                    redis.call('SET', lastLeakKey, now)
                end
                                
                -- Reject if bucket is full
                if count >= capacity then
                    return 0
                end
                                
                -- Add request to bucket
                count = count + 1
                redis.call('SET', countKey, count)
                                
                -- TTL cleanup: expire keys after bucket would fully drain
                local ttl = math.ceil(capacity / leakRate) + 10
                redis.call('EXPIRE', countKey, ttl)
                redis.call('EXPIRE', lastLeakKey, ttl)
                                
                return 1
                """;

        long now = Instant.now().getEpochSecond();

        Long result = redisTemplate.execute(
                org.springframework.data.redis.core.script.RedisScript.of(luaScript, Long.class),
                List.of(countKey, lastLeakKey),
                String.valueOf(now),
                String.valueOf(bucketCapacity),
                String.valueOf(leakRatePerSecond)
        );

        if (result == null || result == 0L) {
            rateLimitBlockedCounter.increment();
            throw new RateLimitExceededException(key);
        }
    }
}