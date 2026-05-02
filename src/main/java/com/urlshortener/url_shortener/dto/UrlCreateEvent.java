package com.urlshortener.url_shortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UrlCreateEvent {
    private Long id;
    private String shortCode;
    private String longUrl;
    private String customAlias;
    private Long userId;
    private Instant expiresAt;
    private Instant createdAt;
}
