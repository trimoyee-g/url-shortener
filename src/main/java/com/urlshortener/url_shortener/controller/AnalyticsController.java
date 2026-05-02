package com.urlshortener.url_shortener.controller;

import com.urlshortener.url_shortener.dto.AnalyticsResponse;
import com.urlshortener.url_shortener.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/{shortCode}/stats")
    public ResponseEntity<AnalyticsResponse> stats(@PathVariable String shortCode) {
        return ResponseEntity.ok(analyticsService.getStats(shortCode));
    }
}