package com.urlshortener.url_shortener.controller;

import com.urlshortener.url_shortener.dto.UrlClickEvent;
import com.urlshortener.url_shortener.service.AnalyticsProducer;
import com.urlshortener.url_shortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final UrlService urlService;
    private final AnalyticsProducer analyticsProducer; // The Kafka producer

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest request) {

        // 1. Resolve (includes Bloom Filter + Redis + DB logic)
        String longUrl = urlService.resolve(shortCode);

        // 2. Publish to Kafka (Async/Non-blocking)
        analyticsProducer.recordClick(UrlClickEvent.builder()
                .shortCode(shortCode)
                .ipAddress(request.getRemoteAddr()) // Use extractIp here too
                .userAgent(request.getHeader("User-Agent"))
                .referer(request.getHeader("Referer"))
                .build());

        // 3. 302 Redirect
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", longUrl)
                .build();
    }
}