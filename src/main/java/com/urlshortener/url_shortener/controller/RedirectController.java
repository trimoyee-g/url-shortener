package com.urlshortener.url_shortener.controller;

import com.urlshortener.url_shortener.dto.UnlockRequest;
import com.urlshortener.url_shortener.dto.UnlockResponse;
import com.urlshortener.url_shortener.dto.UrlClickEvent;
import com.urlshortener.url_shortener.exception.PasswordRequiredException;
import com.urlshortener.url_shortener.service.AnalyticsProducer;
import com.urlshortener.url_shortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final UrlService urlService;
    private final AnalyticsProducer analyticsProducer;

    @GetMapping("/{shortCode:[a-zA-Z0-9]+}")
    public void redirect(
            @PathVariable String shortCode,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        // Resolve: Cuckoo Filter → Redis → MySQL.
        // PasswordRequiredException is thrown by resolve() when the link is protected,
        // after it has confirmed the code exists (either via cache sentinel or DB lookup).
        String longUrl;
        try {
            longUrl = urlService.resolve(shortCode);
        } catch (PasswordRequiredException e) {
            response.sendRedirect("/unlockProtectedLink.html?code=" + shortCode);
            return;
        }

        // Publish click event — swallow any messaging errors so a RabbitMQ
        // blip never breaks a live redirect.
        try {
            analyticsProducer.recordClick(UrlClickEvent.builder()
                    .shortCode(shortCode)
                    .ipAddress(extractIp(request))
                    .userAgent(request.getHeader("User-Agent"))
                    .referer(request.getHeader("Referer"))
                    .build());
        } catch (Exception e) {
            log.warn("Analytics publish failed for {}: {}", shortCode, e.getMessage());
        }

        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", longUrl);
    }

    /**
     * Password unlock endpoint. No authentication required — the link's password IS the credential.
     *
     * <p>On success returns {@code { shortCode, redirectUrl }} and the frontend
     * performs the redirect client-side (so it can record the click too).
     *
     * <p>POST /{shortCode}/unlock
     */
    @PostMapping("/{shortCode}/unlock")
    public ResponseEntity<UnlockResponse> unlock(
            @PathVariable String shortCode,
            @Valid @RequestBody UnlockRequest body,
            HttpServletRequest request) {

        UnlockResponse response = urlService.unlock(shortCode, body);

        // Record the analytics click after successful unlock
        try {
            analyticsProducer.recordClick(UrlClickEvent.builder()
                    .shortCode(shortCode)
                    .ipAddress(extractIp(request))
                    .userAgent(request.getHeader("User-Agent"))
                    .referer(request.getHeader("Referer"))
                    .build());
        } catch (Exception e) {
            log.warn("Analytics publish failed for {}: {}", shortCode, e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
