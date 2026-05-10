package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.exception.RateLimitExceededException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTests {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @InjectMocks
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {

        ReflectionTestUtils.setField(rateLimiterService,
                "bucketCapacity", 20);

        ReflectionTestUtils.setField(rateLimiterService,
                "leakRatePerSecond", 1);

        when(meterRegistry.counter("url.rate_limit.blocked"))
                .thenReturn(counter);

        rateLimiterService.init();
    }

    @Nested
    @DisplayName("checkRateLimit")
    class CheckRateLimit {

        @Test
        @DisplayName("allows request when bucket is not full")
        void allowsRequest_whenWithinLimit() {

            // Arrange
            when(redisTemplate.execute(
                    ArgumentMatchers.<RedisScript<Long>>any(),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(1L);

            // Act + Assert
            assertThatCode(() ->
                    rateLimiterService.checkRateLimit("user-1")
            ).doesNotThrowAnyException();

            verify(redisTemplate).execute(
                    ArgumentMatchers.<RedisScript<Long>>any(),
                    eq(List.of(
                            "rate_limit:user-1:count",
                            "rate_limit:user-1:last_leak"
                    )),
                    anyString(),
                    eq("20"),
                    eq("1")
            );

            verify(counter, never()).increment();
        }

        @Test
        @DisplayName("throws exception when bucket is full")
        void throwsException_whenRateLimitExceeded() {

            // Arrange
            when(redisTemplate.execute(
                    ArgumentMatchers.<RedisScript<Long>>any(),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(0L);

            // Act + Assert
            assertThatThrownBy(() ->
                    rateLimiterService.checkRateLimit("blocked-user")
            )
                    .isInstanceOf(RateLimitExceededException.class)
                    .hasMessage("Rate limit exceeded for: blocked-user");

            verify(counter).increment();
        }

        @Test
        @DisplayName("throws exception when Redis returns null")
        void throwsException_whenRedisReturnsNull() {

            // Arrange
            when(redisTemplate.execute(
                    ArgumentMatchers.<RedisScript<Long>>any(),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(null);

            // Act + Assert
            assertThatThrownBy(() ->
                    rateLimiterService.checkRateLimit("null-user")
            )
                    .isInstanceOf(RateLimitExceededException.class)
                    .hasMessage("Rate limit exceeded for: null-user");

            verify(counter).increment();
        }

        @Test
        @DisplayName("uses correct Redis keys")
        void usesCorrectRedisKeys() {

            // Arrange
            when(redisTemplate.execute(
                    ArgumentMatchers.<RedisScript<Long>>any(),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(1L);

            // Act
            rateLimiterService.checkRateLimit("abc123");

            // Assert
            verify(redisTemplate).execute(
                    ArgumentMatchers.<RedisScript<Long>>any(),
                    eq(List.of(
                            "rate_limit:abc123:count",
                            "rate_limit:abc123:last_leak"
                    )),
                    anyString(),
                    anyString(),
                    anyString()
            );
        }

        @Test
        @DisplayName("passes configured bucket capacity and leak rate")
        void passesConfiguredValues() {

            // Arrange
            ReflectionTestUtils.setField(rateLimiterService,
                    "bucketCapacity", 50);

            ReflectionTestUtils.setField(rateLimiterService,
                    "leakRatePerSecond", 5);

            when(redisTemplate.execute(
                    ArgumentMatchers.<RedisScript<Long>>any(),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(1L);

            // Act
            rateLimiterService.checkRateLimit("config-user");

            // Assert
            verify(redisTemplate).execute(
                    ArgumentMatchers.<RedisScript<Long>>any(),
                    anyList(),
                    anyString(),
                    eq("50"),
                    eq("5")
            );
        }

        @Test
        @DisplayName("does not increment metric when request is allowed")
        void doesNotIncrementMetric_whenAllowed() {

            // Arrange
            when(redisTemplate.execute(
                    ArgumentMatchers.<RedisScript<Long>>any(),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(1L);

            // Act
            rateLimiterService.checkRateLimit("safe-user");

            // Assert
            verify(counter, never()).increment();
        }

        @Test
        @DisplayName("increments metric exactly once when blocked")
        void incrementsMetricOnce_whenBlocked() {

            // Arrange
            when(redisTemplate.execute(
                    ArgumentMatchers.<RedisScript<Long>>any(),
                    anyList(),
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(0L);

            // Act
            assertThatThrownBy(() ->
                    rateLimiterService.checkRateLimit("blocked")
            ).isInstanceOf(RateLimitExceededException.class);

            // Assert
            verify(counter, times(1)).increment();
        }
    }
}