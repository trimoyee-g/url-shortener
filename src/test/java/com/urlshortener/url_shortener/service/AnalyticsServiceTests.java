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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTests {

    @Mock
    private ClickEventRepository clickEventRepository;

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @InjectMocks
    private AnalyticsService analyticsService;

    // ── Setup ────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(analyticsService, "baseUrl", "http://localhost:8080");
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

    /** Stubs the three breakdown queries to return empty lists. */
    private void stubEmptyBreakdowns(String shortCode) {
        when(clickEventRepository.countClicksByReferrerSince(eq(shortCode), any()))
                .thenReturn(List.of());
        when(clickEventRepository.countClicksByDeviceSince(eq(shortCode), any()))
                .thenReturn(List.of());
    }

    // ── getStats ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getStats")
    class GetStats {

        @Test
        @DisplayName("returns analytics response with period-filtered click count")
        void returnsAnalytics_withPeriodFilteredCount() {

            String shortCode = "abc123";

            when(urlRepository.findByShortCodeAndActiveTrue(shortCode))
                    .thenReturn(Optional.of(buildUrl(shortCode)));

            when(clickEventRepository.countClicksSince(eq(shortCode), any()))
                    .thenReturn(25L);

            when(clickEventRepository.countUniqueIpsSince(eq(shortCode), any()))
                    .thenReturn(10L);

            when(clickEventRepository.countClicksByCountrySince(eq(shortCode), any()))
                    .thenReturn(List.of(
                            new Object[]{"IN", 15L},
                            new Object[]{"US", 10L}
                    ));

            when(clickEventRepository.countClicksByDay(eq(shortCode), any()))
                    .thenReturn(List.of(
                            new Object[]{"2026-01-01", 5L},
                            new Object[]{"2026-01-02", 20L}
                    ));

            when(clickEventRepository.countClicksByReferrerSince(eq(shortCode), any()))
                    .thenReturn(Collections.singletonList(new Object[]{null, 5L}));

            when(clickEventRepository.countClicksByDeviceSince(eq(shortCode), any()))
                    .thenReturn(List.of(
                            new Object[]{"DESKTOP", 15L},
                            new Object[]{"MOBILE", 10L}
                    ));

            AnalyticsResponse response = analyticsService.getStats(shortCode, 7);

            assertThat(response.getShortCode()).isEqualTo(shortCode);
            assertThat(response.getShortUrl()).isEqualTo("http://localhost:8080/" + shortCode);
            assertThat(response.getLongUrl()).isEqualTo("https://example.com");
            assertThat(response.getTotalClicks()).isEqualTo(25L);
            assertThat(response.getUniqueVisitors()).isEqualTo(10L);
            assertThat(response.getTopCountries()).hasSize(2);
            assertThat(response.getTopCountries().get(0).getCountry()).isEqualTo("IN");
            assertThat(response.getClicksByDay())
                    .containsEntry("2026-01-01", 5L)
                    .containsEntry("2026-01-02", 20L);
            assertThat(response.getTopReferrers()).hasSize(1);
            assertThat(response.getDeviceBreakdown())
                    .containsEntry("DESKTOP", 15L)
                    .containsEntry("MOBILE", 10L);

            // Redis should NOT be consulted for getStats
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("throws UrlNotFoundException when short code does not exist")
        void throwsException_whenUrlNotFound() {

            when(urlRepository.findByShortCodeAndActiveTrue("ghost"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> analyticsService.getStats("ghost", 7))
                    .isInstanceOf(UrlNotFoundException.class);
        }

        @Test
        @DisplayName("maps null country values to Unknown")
        void mapsNullCountry_toUnknown() {

            String shortCode = "country-test";

            when(urlRepository.findByShortCodeAndActiveTrue(shortCode))
                    .thenReturn(Optional.of(buildUrl(shortCode)));

            when(clickEventRepository.countClicksSince(eq(shortCode), any()))
                    .thenReturn(5L);

            when(clickEventRepository.countUniqueIpsSince(eq(shortCode), any()))
                    .thenReturn(2L);

            when(clickEventRepository.countClicksByCountrySince(eq(shortCode), any()))
                    .thenReturn(Collections.singletonList(new Object[]{null, 5L}));

            when(clickEventRepository.countClicksByDay(eq(shortCode), any()))
                    .thenReturn(Collections.emptyList());

            stubEmptyBreakdowns(shortCode);

            AnalyticsResponse response = analyticsService.getStats(shortCode, 7);

            assertThat(response.getTopCountries()).hasSize(1);
            assertThat(response.getTopCountries().get(0).getCountry()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("limits top countries to maximum 10 entries")
        void limitsTopCountries_toTenEntries() {

            String shortCode = "many-countries";

            when(urlRepository.findByShortCodeAndActiveTrue(shortCode))
                    .thenReturn(Optional.of(buildUrl(shortCode)));

            when(clickEventRepository.countClicksSince(eq(shortCode), any()))
                    .thenReturn(100L);

            when(clickEventRepository.countUniqueIpsSince(eq(shortCode), any()))
                    .thenReturn(50L);

            List<Object[]> countries = new ArrayList<>();
            for (int i = 1; i <= 11; i++) {
                countries.add(new Object[]{"C" + i, (long) i});
            }

            when(clickEventRepository.countClicksByCountrySince(eq(shortCode), any()))
                    .thenReturn(countries);

            when(clickEventRepository.countClicksByDay(eq(shortCode), any()))
                    .thenReturn(List.of());

            stubEmptyBreakdowns(shortCode);

            AnalyticsResponse response = analyticsService.getStats(shortCode, 7);

            assertThat(response.getTopCountries()).hasSize(10);
        }

        @Test
        @DisplayName("returns empty collections when no analytics data exists")
        void returnsEmptyCollections_whenNoDataExists() {

            String shortCode = "empty";

            when(urlRepository.findByShortCodeAndActiveTrue(shortCode))
                    .thenReturn(Optional.of(buildUrl(shortCode)));

            when(clickEventRepository.countClicksSince(eq(shortCode), any()))
                    .thenReturn(0L);

            when(clickEventRepository.countUniqueIpsSince(eq(shortCode), any()))
                    .thenReturn(0L);

            when(clickEventRepository.countClicksByCountrySince(eq(shortCode), any()))
                    .thenReturn(List.of());

            when(clickEventRepository.countClicksByDay(eq(shortCode), any()))
                    .thenReturn(List.of());

            stubEmptyBreakdowns(shortCode);

            AnalyticsResponse response = analyticsService.getStats(shortCode, 7);

            assertThat(response.getTotalClicks()).isZero();
            assertThat(response.getUniqueVisitors()).isZero();
            assertThat(response.getTopCountries()).isEmpty();
            assertThat(response.getTopReferrers()).isEmpty();
            assertThat(response.getClicksByDay()).isEmpty();
        }

        @Test
        @DisplayName("preserves insertion order of click counts by day")
        void preservesOrder_forClicksByDay() {

            String shortCode = "ordered";

            when(urlRepository.findByShortCodeAndActiveTrue(shortCode))
                    .thenReturn(Optional.of(buildUrl(shortCode)));

            when(clickEventRepository.countClicksSince(eq(shortCode), any()))
                    .thenReturn(15L);

            when(clickEventRepository.countUniqueIpsSince(eq(shortCode), any()))
                    .thenReturn(5L);

            when(clickEventRepository.countClicksByCountrySince(eq(shortCode), any()))
                    .thenReturn(List.of());

            when(clickEventRepository.countClicksByDay(eq(shortCode), any()))
                    .thenReturn(List.of(
                            new Object[]{"2026-01-01", 1L},
                            new Object[]{"2026-01-02", 2L},
                            new Object[]{"2026-01-03", 3L}
                    ));

            stubEmptyBreakdowns(shortCode);

            AnalyticsResponse response = analyticsService.getStats(shortCode, 7);

            Map<String, Long> expected = new LinkedHashMap<>();
            expected.put("2026-01-01", 1L);
            expected.put("2026-01-02", 2L);
            expected.put("2026-01-03", 3L);

            assertThat(response.getClicksByDay()).containsExactlyEntriesOf(expected);
        }
    }
}
