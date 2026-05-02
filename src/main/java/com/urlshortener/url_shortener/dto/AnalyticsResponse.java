package com.urlshortener.url_shortener.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data @Builder
public class AnalyticsResponse {
    private String shortCode;
    private String shortUrl;
    private String longUrl;
    private long totalClicks;
    private long uniqueIps;
    private List<CountryCount> topCountries;
    private Map<String, Long> clicksByDay;

    @Data @Builder
    public static class CountryCount {
        private String country;
        private long clicks;
    }
}