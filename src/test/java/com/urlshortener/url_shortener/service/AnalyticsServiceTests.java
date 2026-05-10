package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.AnalyticsResponse;
import com.urlshortener.url_shortener.entity.Url;
import com.urlshortener.url_shortener.exception.UrlNotFoundException;
import com.urlshortener.url_shortener.repository.ClickEventRepository;
import com.urlshortener.url_shortener.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AnalyticsServiceTests {

    @Mock
    private ClickEventRepository clickEventRepository;

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AnalyticsService analyticsService;


    // ── Setup ────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                analyticsService,
                "baseUrl",
                "http://localhost:8080"
        );

        ReflectionTestUtils.setField(
                analyticsService,
                "statsCacheTtl",
                300L
        );
    }


    // ── Helpers ──────────────────────────────────────────────────────────────

    private Url buildUrl(String shortCode) {
        return Url.builder()
                .id(1L)
                .shortCode(shortCode)
                .longUrl("https://example.com")
                .active(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }


    // ── getStats ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getStats")
    class GetStats {

        @Test
        @DisplayName("returns analytics response using Redis click count")
        void returnsAnalytics_usingRedisCounter() {

            // Arrange
            String shortCode = "abc123";

            when(urlRepository.findByShortCodeAndActiveTrue(shortCode))
                    .thenReturn(Optional.of(buildUrl(shortCode)));

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("clicks:" + shortCode))
                    .thenReturn("25");

            when(clickEventRepository.countUniqueIpsByShortCode(shortCode))
                    .thenReturn(10L);

            when(clickEventRepository.countClicksByCountry(shortCode))
                    .thenReturn(List.of(
                            new Object[]{"IN", 15L},
                            new Object[]{"US", 10L}
                    ));

            when(clickEventRepository.countClicksByDay(eq(shortCode), any()))
                    .thenReturn(List.of(
                            new Object[]{"2026-01-01", 5L},
                            new Object[]{"2026-01-02", 20L}
                    ));

            // Act
            AnalyticsResponse response = analyticsService.getStats(shortCode);

            // Assert
            assertThat(response.getShortCode()).isEqualTo(shortCode);
            assertThat(response.getShortUrl())
                    .isEqualTo("http://localhost:8080/" + shortCode);

            assertThat(response.getTotalClicks()).isEqualTo(25L);
            assertThat(response.getUniqueIps()).isEqualTo(10L);

            assertThat(response.getTopCountries()).hasSize(2);

            assertThat(response.getTopCountries().get(0).getCountry())
                    .isEqualTo("IN");

            assertThat(response.getTopCountries().get(0).getClicks())
                    .isEqualTo(15L);

            assertThat(response.getClicksByDay())
                    .containsEntry("2026-01-01", 5L)
                    .containsEntry("2026-01-02", 20L);

            verify(clickEventRepository, never())
                    .countByShortCode(anyString());
        }

        @Test
        @DisplayName("falls back to database when Redis cache is empty")
        void fallsBackToDatabase_whenRedisCacheMiss() {

            // Arrange
            String shortCode = "fallback";

            when(urlRepository.findByShortCodeAndActiveTrue(shortCode))
                    .thenReturn(Optional.of(buildUrl(shortCode)));

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            when(valueOperations.get("clicks:" + shortCode))
                    .thenReturn(null);

            when(clickEventRepository.countByShortCode(shortCode))
                    .thenReturn(50L);

            when(clickEventRepository.countUniqueIpsByShortCode(shortCode))
                    .thenReturn(30L);

            when(clickEventRepository.countClicksByCountry(shortCode))
                    .thenReturn(List.of());

            when(clickEventRepository.countClicksByDay(eq(shortCode), any()))
                    .thenReturn(List.of());

            // Act
            AnalyticsResponse response = analyticsService.getStats(shortCode);

            // Assert
            assertThat(response.getTotalClicks()).isEqualTo(50L);

            verify(clickEventRepository)
                    .countByShortCode(shortCode);

            verify(valueOperations)
                    .set("clicks:" + shortCode, "50");
        }

        @Test
        @DisplayName("throws UrlNotFoundException when short code does not exist")
        void throwsException_whenUrlNotFound() {

            // Arrange
            when(urlRepository.findByShortCodeAndActiveTrue("ghost"))
                    .thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() ->
                    analyticsService.getStats("ghost")
            ).isInstanceOf(UrlNotFoundException.class);
        }

        @Test
        @DisplayName("maps null country values to Unknown")
        void mapsNullCountry_toUnknown() {

            // Arrange
            String shortCode = "country-test";

            when(urlRepository.findByShortCodeAndActiveTrue(shortCode))
                    .thenReturn(Optional.of(buildUrl(shortCode)));

            when(redisTemplate.opsForValue())
                    .thenReturn(valueOperations);

            when(valueOperations.get("clicks:" + shortCode))
                    .thenReturn("5");

            when(clickEventRepository.countUniqueIpsByShortCode(shortCode))
                    .thenReturn(2L);

            List<Object[]> countryData = new ArrayList<>();
            countryData.add(new Object[]{null, 5L});

            when(clickEventRepository.countClicksByCountry(shortCode))
                    .thenReturn(countryData);

            when(clickEventRepository.countClicksByDay(eq(shortCode), any()))
                    .thenReturn(Collections.emptyList());

            // Act
            AnalyticsResponse response = analyticsService.getStats(shortCode);

            // Assert
            assertThat(response.getTopCountries())
                    .hasSize(1);

            assertThat(response.getTopCountries().get(0).getCountry())
                    .isEqualTo("Unknown");

            assertThat(response.getTopCountries().get(0).getClicks())
                    .isEqualTo(5L);
        }

        @Test
        @DisplayName("limits top countries to maximum 10 entries")
        void limitsTopCountries_toTenEntries() {

            // Arrange
            String shortCode = "many-countries";

            when(urlRepository.findByShortCodeAndActiveTrue(shortCode))
                    .thenReturn(Optional.of(buildUrl(shortCode)));

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("clicks:" + shortCode))
                    .thenReturn("100");

            when(clickEventRepository.countUniqueIpsByShortCode(shortCode))
                    .thenReturn(50L);

            List<Object[]> countries = List.of(
                    new Object[]{"C1", 1L},
                    new Object[]{"C2", 2L},
                    new Object[]{"C3", 3L},
                    new Object[]{"C4", 4L},
                    new Object[]{"C5", 5L},
                    new Object[]{"C6", 6L},
                    new Object[]{"C7", 7L},
                    new Object[]{"C8", 8L},
                    new Object[]{"C9", 9L},
                    new Object[]{"C10", 10L},
                    new Object[]{"C11", 11L}
            );

            when(clickEventRepository.countClicksByCountry(shortCode))
                    .thenReturn(countries);

            when(clickEventRepository.countClicksByDay(eq(shortCode), any()))
                    .thenReturn(List.of());

            // Act
            AnalyticsResponse response = analyticsService.getStats(shortCode);

            // Assert
            assertThat(response.getTopCountries())
                    .hasSize(10);
        }

        @Test
        @DisplayName("returns empty collections when no analytics data exists")
        void returnsEmptyCollections_whenNoDataExists() {

            // Arrange
            String shortCode = "empty";

            when(urlRepository.findByShortCodeAndActiveTrue(shortCode))
                    .thenReturn(Optional.of(buildUrl(shortCode)));

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("clicks:" + shortCode))
                    .thenReturn("0");

            when(clickEventRepository.countUniqueIpsByShortCode(shortCode))
                    .thenReturn(0L);

            when(clickEventRepository.countClicksByCountry(shortCode))
                    .thenReturn(List.of());

            when(clickEventRepository.countClicksByDay(eq(shortCode), any()))
                    .thenReturn(List.of());

            // Act
            AnalyticsResponse response = analyticsService.getStats(shortCode);

            // Assert
            assertThat(response.getTotalClicks()).isZero();
            assertThat(response.getUniqueIps()).isZero();
            assertThat(response.getTopCountries()).isEmpty();
            assertThat(response.getClicksByDay()).isEmpty();
        }

        @Test
        @DisplayName("preserves insertion order of click counts by day")
        void preservesOrder_forClicksByDay() {

            // Arrange
            String shortCode = "ordered";

            when(urlRepository.findByShortCodeAndActiveTrue(shortCode))
                    .thenReturn(Optional.of(buildUrl(shortCode)));

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("clicks:" + shortCode))
                    .thenReturn("15");

            when(clickEventRepository.countUniqueIpsByShortCode(shortCode))
                    .thenReturn(5L);

            when(clickEventRepository.countClicksByCountry(shortCode))
                    .thenReturn(List.of());

            when(clickEventRepository.countClicksByDay(eq(shortCode), any()))
                    .thenReturn(List.of(
                            new Object[]{"2026-01-01", 1L},
                            new Object[]{"2026-01-02", 2L},
                            new Object[]{"2026-01-03", 3L}
                    ));

            // Act
            AnalyticsResponse response = analyticsService.getStats(shortCode);

            // Assert
            Map<String, Long> expected = new LinkedHashMap<>();
            expected.put("2026-01-01", 1L);
            expected.put("2026-01-02", 2L);
            expected.put("2026-01-03", 3L);

            assertThat(response.getClicksByDay())
                    .containsExactlyEntriesOf(expected);
        }
    }
}