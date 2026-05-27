package com.urlshortener.url_shortener.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Returned by POST /{shortCode}/unlock on successful password verification.
 * The frontend should redirect the user to {@code redirectUrl}.
 */
@Data
@Builder
public class UnlockResponse {
    private String shortCode;
    private String redirectUrl;
}
