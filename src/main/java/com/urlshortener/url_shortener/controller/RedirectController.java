package com.urlshortener.url_shortener.controller;

import com.urlshortener.url_shortener.dto.UrlClickEvent;
import com.urlshortener.url_shortener.service.AnalyticsProducer;
import com.urlshortener.url_shortener.service.GeoIpService;
import com.urlshortener.url_shortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final UrlService urlService;
    private final AnalyticsProducer analyticsProducer;
    private final GeoIpService geoIpService;

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest request) {

        // 1. Resolve (includes Bloom Filter + Redis + DB logic)
        String longUrl = urlService.resolve(shortCode);

        // 2. Extract IP
        String ip = extractIp(request);

        // 3. GeoIP lookup — falls back to "XX" if unavailable
        String country = geoIpService.getCountryCode(ip);

        // 4. Publish to Kafka (Async/Non-blocking)
        analyticsProducer.recordClick(UrlClickEvent.builder()
                .shortCode(shortCode)
                .ipAddress(ip)
                .userAgent(request.getHeader("User-Agent"))
                .referer(request.getHeader("Referer"))
                .country(country)
                .build());

        // 5. 302 Redirect
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", longUrl)
                .build();
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}