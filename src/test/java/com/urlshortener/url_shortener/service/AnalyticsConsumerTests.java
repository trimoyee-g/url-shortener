package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.UrlClickEvent;
import com.urlshortener.url_shortener.entity.ClickEvent;
import com.urlshortener.url_shortener.repository.ClickEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AnalyticsConsumerTests {

    @Mock
    private ClickEventRepository clickEventRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AnalyticsConsumer analyticsConsumer;


    // ── Helpers ──────────────────────────────────────────────────────────────

    private UrlClickEvent buildEvent(String shortCode) {
        return UrlClickEvent.builder()
                .shortCode(shortCode)
                .longUrl("https://example.com")
                .timestamp("2026-01-01T00:00:00Z")
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .referer("https://google.com")
                .build();
    }


    // ── handleClickEvent ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleClickEvent")
    class HandleClickEvent {

        @Test
        @DisplayName("saves click event to database")
        void savesClickEvent_toDatabase() {

            // Arrange
            UrlClickEvent dto = buildEvent("abc123");

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // Act
            analyticsConsumer.handleClickEvent(dto);

            // Assert
            ArgumentCaptor<ClickEvent> eventCaptor =
                    ArgumentCaptor.forClass(ClickEvent.class);

            verify(clickEventRepository).save(eventCaptor.capture());

            ClickEvent savedEvent = eventCaptor.getValue();

            assertThat(savedEvent.getShortCode()).isEqualTo("abc123");
            assertThat(savedEvent.getIpAddress()).isEqualTo("192.168.1.1");
            assertThat(savedEvent.getUserAgent()).isEqualTo("Mozilla/5.0");
            assertThat(savedEvent.getReferrer()).isEqualTo("https://google.com");
        }

        @Test
        @DisplayName("increments Redis click counter")
        void incrementsRedisCounter() {

            // Arrange
            UrlClickEvent dto = buildEvent("redis-code");

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // Act
            analyticsConsumer.handleClickEvent(dto);

            // Assert
            verify(valueOperations)
                    .increment("clicks:redis-code");
        }

        @Test
        @DisplayName("saves database record before incrementing Redis counter")
        void savesBeforeIncrementingRedis() {

            // Arrange
            UrlClickEvent dto = buildEvent("ordered");

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // Act
            analyticsConsumer.handleClickEvent(dto);

            // Assert
            verify(clickEventRepository).save(any(ClickEvent.class));
            verify(valueOperations).increment("clicks:ordered");

            verifyNoMoreInteractions(clickEventRepository, valueOperations);
        }

        @Test
        @DisplayName("handles nullable optional fields correctly")
        void handlesNullableFields() {

            // Arrange
            UrlClickEvent dto = UrlClickEvent.builder()
                    .shortCode("minimal")
                    .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // Act
            analyticsConsumer.handleClickEvent(dto);

            // Assert
            ArgumentCaptor<ClickEvent> eventCaptor =
                    ArgumentCaptor.forClass(ClickEvent.class);

            verify(clickEventRepository).save(eventCaptor.capture());

            ClickEvent savedEvent = eventCaptor.getValue();

            assertThat(savedEvent.getShortCode()).isEqualTo("minimal");
            assertThat(savedEvent.getIpAddress()).isNull();
            assertThat(savedEvent.getUserAgent()).isNull();
            assertThat(savedEvent.getReferrer()).isNull();

            verify(valueOperations).increment("clicks:minimal");
        }

        @Test
        @DisplayName("does not throw when repository save fails")
        void doesNotThrow_whenRepositoryFails() {

            // Arrange
            UrlClickEvent dto = buildEvent("db-failure");

            when(clickEventRepository.save(any(ClickEvent.class)))
                    .thenThrow(new RuntimeException("DB failure"));

            // Act
            analyticsConsumer.handleClickEvent(dto);

            // Assert
            verify(clickEventRepository).save(any(ClickEvent.class));

            verify(redisTemplate, never()).opsForValue();
            verify(valueOperations, never()).increment(anyString());
        }

        @Test
        @DisplayName("does not throw when Redis increment fails")
        void doesNotThrow_whenRedisFails() {

            // Arrange
            UrlClickEvent dto = buildEvent("redis-failure");

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            doThrow(new RuntimeException("Redis failure"))
                    .when(valueOperations)
                    .increment("clicks:redis-failure");

            // Act
            analyticsConsumer.handleClickEvent(dto);

            // Assert
            verify(clickEventRepository).save(any(ClickEvent.class));

            verify(valueOperations)
                    .increment("clicks:redis-failure");
        }

        @Test
        @DisplayName("processes multiple click events independently")
        void processesMultipleEventsIndependently() {

            // Arrange
            UrlClickEvent first = buildEvent("code-1");
            UrlClickEvent second = buildEvent("code-2");

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // Act
            analyticsConsumer.handleClickEvent(first);
            analyticsConsumer.handleClickEvent(second);

            // Assert
            verify(clickEventRepository, times(2))
                    .save(any(ClickEvent.class));

            verify(valueOperations).increment("clicks:code-1");
            verify(valueOperations).increment("clicks:code-2");
        }
    }
}