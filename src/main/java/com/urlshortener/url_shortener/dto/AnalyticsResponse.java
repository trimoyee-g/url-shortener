package com.urlshortener.url_shortener.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AnalyticsResponse {

    private String shortCode;
    private String shortUrl;
    private String longUrl;

    private long totalClicks;
    private long uniqueVisitors;

    /** ISO-3166 country code → click count, sorted desc, capped at 10. */
    private List<CountryCount> topCountries;

    /** Referrer hostname → click count, sorted desc, capped at 10. */
    private List<ReferrerCount> topReferrers;

    /** "MOBILE" | "TABLET" | "DESKTOP" → click count. */
    private Map<String, Long> deviceBreakdown;

    /** Date string (yyyy-MM-dd) → click count, ordered chronologically. */
    private Map<String, Long> clicksByDay;

    // ── Nested value types ───────────────────────────────────────────────────

    @Data
    @Builder
    public static class CountryCount {
        private String country;
        private long clicks;
    }

    @Data
    @Builder
    public static class ReferrerCount {
        private String referrer;
        private long clicks;
    }
}
