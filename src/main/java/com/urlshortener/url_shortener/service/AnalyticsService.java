package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.AnalyticsResponse;
// import com.urlshortener.url_shortener.entity.ClickEvent;
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

    @Value("${app.cache.stats-ttl-seconds:300}")
    private long statsCacheTtl;

    public AnalyticsResponse getStats(String shortCode) {
        urlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        // Use the Redis counter we update in the Kafka Consumer
        long total = getClickCount(shortCode);
        long unique = clickEventRepository.countUniqueIpsByShortCode(shortCode);

        List<AnalyticsResponse.CountryCount> countries =
                clickEventRepository.countClicksByCountry(shortCode).stream()
                        .map(row -> AnalyticsResponse.CountryCount.builder()
                                .country((String) row[0] != null ? (String) row[0] : "Unknown")
                                .clicks((Long) row[1])
                                .build())
                        .limit(10)
                        .toList();

        Instant since = Instant.now().minus(Duration.ofDays(30));
        Map<String, Long> byDay = clickEventRepository
                .countClicksByDay(shortCode, since).stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1],
                        (a, b) -> a,
                        LinkedHashMap::new));

        return AnalyticsResponse.builder()
                .shortCode(shortCode)
                .shortUrl(baseUrl + "/" + shortCode)
                .totalClicks(total)
                .uniqueIps(unique)
                .topCountries(countries)
                .clicksByDay(byDay)
                .build();
    }

    private long getClickCount(String shortCode) {
        String cached = redisTemplate.opsForValue().get("clicks:" + shortCode);
        if (cached != null) return Long.parseLong(cached);

        // Fallback to DB if Redis counter is empty
        long count = clickEventRepository.countByShortCode(shortCode);
        redisTemplate.opsForValue().set("clicks:" + shortCode, String.valueOf(count));
        return count;
    }
}