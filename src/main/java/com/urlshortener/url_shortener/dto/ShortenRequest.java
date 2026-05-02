package com.urlshortener.url_shortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class ShortenRequest {

    @NotBlank(message = "URL is required")
    @URL(message = "Must be a valid URL")
    private String longUrl;

    @Size(min = 3, max = 20, message = "Alias must be 3-20 characters") // 20 is usually safer for UI
    @Pattern(regexp = "^[a-zA-Z0-9_-]*$",
            message = "Alias may only contain letters, digits, hyphens, and underscores")
    private String customAlias;

    private Long ttlSeconds;

    /**
     * Normalizes the URL by removing trailing slashes and converting to lowercase host.
     * This ensures the Snowflake ID generation is idempotent for the same actual destination.
     */
    public String getNormalizedUrl() {
        if (longUrl == null) return null;
        String trimmed = longUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}