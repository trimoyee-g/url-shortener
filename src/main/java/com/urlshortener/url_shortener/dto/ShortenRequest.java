package com.urlshortener.url_shortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShortenRequest {

    @NotBlank(message = "URL is required")
    @URL(message = "Must be a valid URL")
    private String longUrl;

    @Size(min = 3, max = 20, message = "Alias must be 3-20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]*$",
            message = "Alias may only contain letters, digits, hyphens, and underscores")
    private String customAlias;

    private Long ttlSeconds;

    // ── UTM tracking parameters (all optional) ────────────────────────────────

    @Size(max = 100, message = "utm_source must be at most 100 characters")
    private String utmSource;

    @Size(max = 100, message = "utm_medium must be at most 100 characters")
    private String utmMedium;

    @Size(max = 100, message = "utm_campaign must be at most 100 characters")
    private String utmCampaign;

    // ── Password protection (optional) ────────────────────────────────────────

    /** When set, visitors must supply this password before being redirected. */
    @Size(max = 72, message = "Password must be at most 72 characters")
    private String password;

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Normalizes the URL by removing trailing slashes.
     * This ensures idempotent short-code generation for the same destination.
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
