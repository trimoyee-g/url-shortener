package com.urlshortener.url_shortener.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Aggregate analytics response for the authenticated user's entire account.
 * Returned by GET /api/v1/analytics/dashboard?days=30.
 */
@Data
@Builder
public class DashboardResponse {

    // ── Summary cards ─────────────────────────────────────────────────────────

    /** Total clicks across all user's active links in the requested window. */
    private long totalClicks;

    /** Count of active short links owned by the user. */
    private long totalLinks;

    /** Distinct IPs across all user's links in the requested window. */
    private long uniqueVisitors;

    /** Percentage change in totalClicks vs. the equivalent prior period. Null when no prior data. */
    private Double clicksChangePct;

    // ── Time series ───────────────────────────────────────────────────────────

    /** Date string (yyyy-MM-dd) → click count, ordered chronologically. */
    private Map<String, Long> clicksByDay;

    // ── Breakdowns ────────────────────────────────────────────────────────────

    /** ISO-3166 country code → click count, sorted desc, capped at 10. */
    private List<AnalyticsResponse.CountryCount> topCountries;

    /** Referrer hostname → click count, sorted desc, capped at 10. */
    private List<AnalyticsResponse.ReferrerCount> topReferrers;

    /** "MOBILE" | "TABLET" | "DESKTOP" → click count. */
    private Map<String, Long> deviceBreakdown;

    /** Number of days this response covers (mirrors the ?days query param). */
    private int days;
}
