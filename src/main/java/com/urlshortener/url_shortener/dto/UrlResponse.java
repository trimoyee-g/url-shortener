package com.urlshortener.url_shortener.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class UrlResponse {

    private String shortUrl;
    private String shortCode;
    private String longUrl;
    private String customAlias;
    private Instant expiresAt;
    private Instant createdAt;

    /** Total lifetime click count (from Redis cache or DB fallback). */
    private long clickCount;

    /** ACTIVE while the link is live; EXPIRED once expiresAt has passed. */
    private LinkStatus status;

    public enum LinkStatus {
        ACTIVE, EXPIRED
    }
}
