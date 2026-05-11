package com.urlshortener.url_shortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UrlClickEvent {
    private String shortCode;
    private String longUrl;
    private String timestamp;
    private String ipAddress;
    private String userAgent;
    private String country; // populated by GeoIP lookup before publishing
    private String referer;
}
