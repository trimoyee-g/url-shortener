package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.UrlCreateEvent;
import com.urlshortener.url_shortener.entity.Url;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.repository.UrlRepository;
import com.urlshortener.url_shortener.repository.UserRepository;
import com.urlshortener.url_shortener.util.UrlCuckooFilter;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlCreateConsumerTests {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UrlCuckooFilter cuckooFilter;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UrlCreateConsumer urlCreateConsumer;

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {

        ReflectionTestUtils.setField(
                urlCreateConsumer,
                "cacheTtlSeconds",
                86400L
        );

        // lenient because some failure tests never reach Redis
        lenient().when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);
    }

    // ─────────────────────────────────────────────────────────────

    private UrlCreateEvent buildEvent() {
        return UrlCreateEvent.builder()
                .id(1L)
                .shortCode("abc123")
                .longUrl("https://google.com")
                .customAlias("google")
                .userId(10L)
                .createdAt(NOW)
                .expiresAt(NOW.plusSeconds(3600))
                .build();
    }

    private User buildUser() {
        return User.builder()
                .id(10L)
                .email("test@example.com")
                .password("password")
                .name("Test User")
                .build();
    }

    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleCreateEvent")
    class HandleCreateEvent {

        @Test
        @DisplayName("saves URL, updates cuckoo filter, and caches URL")
        void processesEventSuccessfully() {

            // Arrange
            UrlCreateEvent event = buildEvent();
            User user = buildUser();

            when(userRepository.findById(10L))
                    .thenReturn(Optional.of(user));

            // Act
            urlCreateConsumer.handleCreateEvent(event);

            // Assert URL save
            ArgumentCaptor<Url> urlCaptor =
                    ArgumentCaptor.forClass(Url.class);

            verify(urlRepository).save(urlCaptor.capture());

            Url saved = urlCaptor.getValue();

            assertThat(saved.getId()).isEqualTo(1L);
            assertThat(saved.getShortCode()).isEqualTo("abc123");
            assertThat(saved.getLongUrl()).isEqualTo("https://google.com");
            assertThat(saved.getCustomAlias()).isEqualTo("google");
            assertThat(saved.getUser()).isEqualTo(user);
            assertThat(saved.isActive()).isTrue();

            // Assert bloom filter
            verify(cuckooFilter).add("abc123");

            // Assert Redis cache
            verify(valueOperations).set(
                    eq("url:abc123"),
                    eq("https://google.com"),
                    any(Duration.class)
            );
        }

        @Test
        @DisplayName("handles missing user gracefully")
        void handlesMissingUser() {

            // Arrange
            UrlCreateEvent event = buildEvent();

            when(userRepository.findById(10L))
                    .thenReturn(Optional.empty());

            // Act
            urlCreateConsumer.handleCreateEvent(event);

            // Assert
            ArgumentCaptor<Url> captor =
                    ArgumentCaptor.forClass(Url.class);

            verify(urlRepository).save(captor.capture());

            Url saved = captor.getValue();

            assertThat(saved.getUser()).isNull();

            verify(cuckooFilter).add("abc123");

            verify(valueOperations).set(
                    eq("url:abc123"),
                    eq("https://google.com"),
                    any(Duration.class)
            );
        }

        @Test
        @DisplayName("uses expiry-based TTL when expiresAt exists")
        void usesExpiryBasedTtl() {

            // Arrange
            UrlCreateEvent event = buildEvent();

            when(userRepository.findById(anyLong()))
                    .thenReturn(Optional.of(buildUser()));

            // Act
            urlCreateConsumer.handleCreateEvent(event);

            // Assert
            ArgumentCaptor<Duration> ttlCaptor =
                    ArgumentCaptor.forClass(Duration.class);

            verify(valueOperations).set(
                    eq("url:abc123"),
                    eq("https://google.com"),
                    ttlCaptor.capture()
            );

            Duration ttl = ttlCaptor.getValue();

            assertThat(ttl.getSeconds())
                    .isBetween(1L, 3600L);
        }

        @Test
        @DisplayName("uses default cache TTL when expiry is null")
        void usesDefaultCacheTtl_whenExpiryNull() {

            // Arrange
            UrlCreateEvent event = buildEvent();
            event.setExpiresAt(null);

            when(userRepository.findById(anyLong()))
                    .thenReturn(Optional.of(buildUser()));

            // Act
            urlCreateConsumer.handleCreateEvent(event);

            // Assert
            verify(valueOperations).set(
                    eq("url:abc123"),
                    eq("https://google.com"),
                    eq(Duration.ofSeconds(86400))
            );
        }

        @Test
        @DisplayName("handles database failure gracefully")
        void handlesDatabaseFailure_gracefully() {

            // Arrange
            UrlCreateEvent event = buildEvent();

            when(userRepository.findById(anyLong()))
                    .thenReturn(Optional.of(buildUser()));

            doThrow(new RuntimeException("DB failure"))
                    .when(urlRepository)
                    .save(any(Url.class));

            // Act
            urlCreateConsumer.handleCreateEvent(event);

            // Assert
            verify(urlRepository).save(any(Url.class));

            // Since DB failed first,
            // these should never execute
            verify(cuckooFilter, never()).add(anyString());

            verify(valueOperations, never()).set(
                    anyString(),
                    anyString(),
                    any(Duration.class)
            );
        }

        @Test
        @DisplayName("handles Redis failure gracefully")
        void handlesRedisFailure_gracefully() {

            // Arrange
            UrlCreateEvent event = buildEvent();

            when(userRepository.findById(anyLong()))
                    .thenReturn(Optional.of(buildUser()));

            doThrow(new RuntimeException("Redis failure"))
                    .when(valueOperations)
                    .set(anyString(), anyString(), any(Duration.class));

            // Act
            urlCreateConsumer.handleCreateEvent(event);

            // Assert
            verify(urlRepository).save(any(Url.class));

            verify(cuckooFilter).add("abc123");

            verify(valueOperations).set(
                    eq("url:abc123"),
                    eq("https://google.com"),
                    any(Duration.class)
            );
        }

        @Test
        @DisplayName("handles cuckoo filter failure gracefully")
        void handlesBloomFilterFailure_gracefully() {

            // Arrange
            UrlCreateEvent event = buildEvent();

            when(userRepository.findById(anyLong()))
                    .thenReturn(Optional.of(buildUser()));

            doThrow(new RuntimeException("Bloom filter failure"))
                    .when(cuckooFilter)
                    .add(anyString());

            // Act
            urlCreateConsumer.handleCreateEvent(event);

            // Assert
            verify(urlRepository).save(any(Url.class));

            verify(cuckooFilter).add("abc123");

            // Redis should not execute because bloom failed first
            verify(valueOperations, never()).set(
                    anyString(),
                    anyString(),
                    any(Duration.class)
            );
        }
    }
}