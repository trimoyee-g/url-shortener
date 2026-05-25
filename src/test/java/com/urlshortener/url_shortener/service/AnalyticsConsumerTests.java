package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.UrlClickEvent;
import com.urlshortener.url_shortener.entity.ClickEvent;
import com.urlshortener.url_shortener.repository.ClickEventRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AnalyticsConsumerTests {

    @Mock
    private ClickEventRepository clickEventRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private GeoIpService geoIpService;

    @InjectMocks
    private AnalyticsConsumer analyticsConsumer;


    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(geoIpService.getCountryCode(anyString())).thenReturn("US");
    }


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
            UrlClickEvent dto = buildEvent("abc123");

            analyticsConsumer.handleClickEvent(dto);

            ArgumentCaptor<ClickEvent> eventCaptor = ArgumentCaptor.forClass(ClickEvent.class);
            verify(clickEventRepository).save(eventCaptor.capture());

            ClickEvent saved = eventCaptor.getValue();
            assertThat(saved.getShortCode()).isEqualTo("abc123");
            assertThat(saved.getIpAddress()).isEqualTo("192.168.1.1");
            assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
            assertThat(saved.getReferrer()).isEqualTo("https://google.com");
        }

        @Test
        @DisplayName("increments Redis click counter")
        void incrementsRedisCounter() {
            analyticsConsumer.handleClickEvent(buildEvent("redis-code"));

            verify(valueOperations).increment("clicks:redis-code");
        }

        @Test
        @DisplayName("saves database record before incrementing Redis counter")
        void savesBeforeIncrementingRedis() {
            analyticsConsumer.handleClickEvent(buildEvent("ordered"));

            verify(clickEventRepository).save(any(ClickEvent.class));
            verify(valueOperations).increment("clicks:ordered");
            verifyNoMoreInteractions(clickEventRepository, valueOperations);
        }

        @Test
        @DisplayName("does not throw when repository save fails")
        void doesNotThrow_whenRepositoryFails() {
            when(clickEventRepository.save(any(ClickEvent.class)))
                    .thenThrow(new RuntimeException("DB failure"));

            analyticsConsumer.handleClickEvent(buildEvent("db-failure"));

            verify(clickEventRepository).save(any(ClickEvent.class));
            verify(redisTemplate, never()).opsForValue();
            verify(valueOperations, never()).increment(anyString());
        }

        @Test
        @DisplayName("does not throw when Redis increment fails")
        void doesNotThrow_whenRedisFails() {
            doThrow(new RuntimeException("Redis failure"))
                    .when(valueOperations).increment("clicks:redis-failure");

            analyticsConsumer.handleClickEvent(buildEvent("redis-failure"));

            verify(clickEventRepository).save(any(ClickEvent.class));
            verify(valueOperations).increment("clicks:redis-failure");
        }

        @Test
        @DisplayName("processes multiple click events independently")
        void processesMultipleEventsIndependently() {
            analyticsConsumer.handleClickEvent(buildEvent("code-1"));
            analyticsConsumer.handleClickEvent(buildEvent("code-2"));

            verify(clickEventRepository, times(2)).save(any(ClickEvent.class));
            verify(valueOperations).increment("clicks:code-1");
            verify(valueOperations).increment("clicks:code-2");
        }
    }
}
