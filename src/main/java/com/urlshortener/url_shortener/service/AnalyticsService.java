package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.AnalyticsResponse;
import com.urlshortener.url_shortener.dto.DashboardResponse;
import com.urlshortener.url_shortener.repository.ClickEventRepository;
import com.urlshortener.url_shortener.repository.UrlRepository;
import com.urlshortener.url_shortener.exception.UrlNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ClickEventRepository clickEventRepository;
    private final UrlRepository urlRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.base-url}")
    private String baseUrl;

    // ── Per-link stats ────────────────────────────────────────────────────────

    /**
     * Returns full analytics for a single short link.
     *
     * @param shortCode the link to query
     * @param days      rolling window in days (7, 30, 90)
     */
    public AnalyticsResponse getStats(String shortCode, int days) {
        var url = urlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        Instant since = Instant.now().minus(Duration.ofDays(days));

        long total  = clickEventRepository.countClicksSince(shortCode, since);
        long unique = clickEventRepository.countUniqueIpsSince(shortCode, since);

        List<AnalyticsResponse.CountryCount> countries = toCountryCounts(
                clickEventRepository.countClicksByCountrySince(shortCode, since));

        List<AnalyticsResponse.ReferrerCount> referrers = toReferrerCounts(
                clickEventRepository.countClicksByReferrerSince(shortCode, since));

        Map<String, Long> deviceBreakdown = toDeviceMap(
                clickEventRepository.countClicksByDeviceSince(shortCode, since));

        Map<String, Long> byDay = toDayMap(
                clickEventRepository.countClicksByDay(shortCode, since));

        return AnalyticsResponse.builder()
                .shortCode(shortCode)
                .shortUrl(baseUrl + "/" + shortCode)
                .longUrl(url.getLongUrl())
                .totalClicks(total)
                .uniqueVisitors(unique)
                .topCountries(countries)
                .topReferrers(referrers)
                .deviceBreakdown(deviceBreakdown)
                .clicksByDay(byDay)
                .build();
    }

    // ── Dashboard aggregate ───────────────────────────────────────────────────

    /**
     * Returns user-level aggregate analytics across all of the user's active links.
     *
     * @param userEmail authenticated user's email
     * @param days      rolling window in days
     */
    public DashboardResponse getDashboard(String userEmail, int days) {
        List<String> codes = urlRepository.findActiveShortCodesByUserEmail(userEmail);
        long totalLinks    = urlRepository.countActiveByUserEmail(userEmail);

        if (codes.isEmpty()) {
            return DashboardResponse.builder()
                    .totalClicks(0).totalLinks(totalLinks).uniqueVisitors(0)
                    .clicksChangePct(null)
                    .clicksByDay(Collections.emptyMap())
                    .topCountries(Collections.emptyList())
                    .topReferrers(Collections.emptyList())
                    .deviceBreakdown(Collections.emptyMap())
                    .days(days)
                    .build();
        }

        Instant now        = Instant.now();
        Instant since      = now.minus(Duration.ofDays(days));
        Instant priorStart = since.minus(Duration.ofDays(days));

        long totalClicks    = clickEventRepository.countTotalClicksForCodes(codes, since);
        long uniqueVisitors = clickEventRepository.countUniqueVisitorsForCodes(codes, since);

        // % change vs the equivalent prior period (rounded to 1 decimal place)
        long priorClicks = clickEventRepository.countClicksForCodesBetween(codes, priorStart, since);
        Double changePct = priorClicks > 0
                ? Math.round(((double)(totalClicks - priorClicks) / priorClicks) * 1000.0) / 10.0
                : null;

        List<AnalyticsResponse.CountryCount> topCountries = toCountryCounts(
                clickEventRepository.countClicksByCountryForCodes(codes, since));

        List<AnalyticsResponse.ReferrerCount> topReferrers = toReferrerCounts(
                clickEventRepository.countClicksByReferrerForCodes(codes, since));

        Map<String, Long> deviceBreakdown = toDeviceMap(
                clickEventRepository.countClicksByDeviceForCodes(codes, since));

        Map<String, Long> clicksByDay = toDayMap(
                clickEventRepository.countClicksByDayForCodes(codes, since));

        return DashboardResponse.builder()
                .totalClicks(totalClicks)
                .totalLinks(totalLinks)
                .uniqueVisitors(uniqueVisitors)
                .clicksChangePct(changePct)
                .clicksByDay(clicksByDay)
                .topCountries(topCountries)
                .topReferrers(topReferrers)
                .deviceBreakdown(deviceBreakdown)
                .days(days)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long getClickCount(String shortCode) {
        String cached = redisTemplate.opsForValue().get("clicks:" + shortCode);
        if (cached != null) return Long.parseLong(cached);
        return clickEventRepository.countByShortCode(shortCode);
    }

    private List<AnalyticsResponse.CountryCount> toCountryCounts(List<Object[]> rows) {
        return rows.stream()
                .limit(10)
                .map(row -> AnalyticsResponse.CountryCount.builder()
                        .country(row[0] != null ? (String) row[0] : "Unknown")
                        .clicks((Long) row[1])
                        .build())
                .toList();
    }

    private List<AnalyticsResponse.ReferrerCount> toReferrerCounts(List<Object[]> rows) {
        return rows.stream()
                .limit(10)
                .map(row -> AnalyticsResponse.ReferrerCount.builder()
                        .referrer(row[0] != null ? (String) row[0] : "Direct")
                        .clicks((Long) row[1])
                        .build())
                .toList();
    }

    private Map<String, Long> toDeviceMap(List<Object[]> rows) {
        // Always return all three keys so the frontend never has to null-check
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("DESKTOP", 0L);
        result.put("MOBILE", 0L);
        result.put("TABLET", 0L);
        rows.forEach(row -> result.put(row[0].toString(), (Long) row[1]));
        return result;
    }

    private Map<String, Long> toDayMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> row[0].toString(),
                row -> (Long) row[1],
                (a, b) -> a,
                LinkedHashMap::new));
    }
}
