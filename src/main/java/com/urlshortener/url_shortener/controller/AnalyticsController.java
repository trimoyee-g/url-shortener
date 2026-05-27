package com.urlshortener.url_shortener.controller;

import com.urlshortener.url_shortener.dto.AnalyticsResponse;
import com.urlshortener.url_shortener.dto.DashboardResponse;
import com.urlshortener.url_shortener.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Per-link analytics.
     * GET /api/v1/urls/{shortCode}/stats?days=30
     */
    @GetMapping("/api/v1/urls/{shortCode}/stats")
    public ResponseEntity<AnalyticsResponse> stats(
            @PathVariable String shortCode,
            @RequestParam(defaultValue = "30") int days) {

        days = clampDays(days);
        return ResponseEntity.ok(analyticsService.getStats(shortCode, days));
    }

    /**
     * Account-level aggregate analytics for the authenticated user.
     * GET /api/v1/analytics/dashboard?days=30
     */
    @GetMapping("/api/v1/analytics/dashboard")
    public ResponseEntity<DashboardResponse> dashboard(
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal UserDetails principal) {

        days = clampDays(days);
        return ResponseEntity.ok(analyticsService.getDashboard(principal.getUsername(), days));
    }

    /** Clamps the days param to sensible bounds: minimum 1, maximum 365. */
    private int clampDays(int days) {
        return Math.max(1, Math.min(days, 365));
    }
}
