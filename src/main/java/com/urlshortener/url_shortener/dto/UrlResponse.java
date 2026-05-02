package com.urlshortener.url_shortener.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data @Builder
public class UrlResponse {
    private String shortUrl;
    private String shortCode;
    private String longUrl;
    private String customAlias;
    private Instant expiresAt;
    private Instant createdAt;
}