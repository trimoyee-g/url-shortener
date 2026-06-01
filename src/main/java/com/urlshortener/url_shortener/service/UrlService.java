package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.PagedResponse;
import com.urlshortener.url_shortener.dto.ShortenRequest;
import com.urlshortener.url_shortener.dto.UnlockRequest;
import com.urlshortener.url_shortener.dto.UnlockResponse;
import com.urlshortener.url_shortener.dto.UrlResponse;

public interface UrlService {
    UrlResponse shorten(ShortenRequest request, String userEmail, String remoteAddr);
    String resolve(String shortCode);
    void delete(String shortCode, String userEmail);
    PagedResponse<UrlResponse> getUserUrls(String userEmail, int page);

    /**
     * Verify a password for a password-protected short link and return the destination URL.
     * Throws BadCredentialsException on a wrong password.
     */
    UnlockResponse unlock(String shortCode, UnlockRequest request);

    /**
     * Generate a PNG QR code for the given short code. Returns raw PNG bytes.
     * @param shortCode short code / alias
     * @param size pixel width/height of the square QR image
     */
    byte[] generateQr(String shortCode, int size);
}
