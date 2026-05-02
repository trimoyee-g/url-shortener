package com.urlshortener.url_shortener.controller;

import com.urlshortener.url_shortener.dto.ShortenRequest;
import com.urlshortener.url_shortener.dto.UrlResponse;
import com.urlshortener.url_shortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;

    @PostMapping("/shorten")
    public ResponseEntity<UrlResponse> shorten(
            @Valid @RequestBody ShortenRequest request,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest httpRequest) {

        // We pass the remote address to the service so it can handle
        // both IP-based and User-based rate limiting in one transaction.
        String remoteAddr = extractIp(httpRequest);

        UrlResponse response = urlService.shorten(request, principal.getUsername(), remoteAddr);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<UrlResponse>> myUrls(
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(urlService.getUserUrls(principal.getUsername()));
    }

    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> delete(
            @PathVariable String shortCode,
            @AuthenticationPrincipal UserDetails principal) {
        urlService.delete(shortCode, principal.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{shortCode}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(
            @PathVariable String shortCode,
            @RequestParam(required = false, defaultValue = "300") int size) {

        byte[] png = urlService.generateQr(shortCode, size);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    /**
     * Extracts the true client IP, accounting for Load Balancers/Proxies.
     */
    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}