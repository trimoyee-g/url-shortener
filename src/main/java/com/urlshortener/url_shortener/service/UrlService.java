package com.urlshortener.url_shortener.service;


import com.urlshortener.url_shortener.dto.ShortenRequest;
import com.urlshortener.url_shortener.dto.UrlResponse;

import java.util.List;

public interface UrlService {
    UrlResponse shorten(ShortenRequest request, String userEmail, String remoteAddr);
    String resolve(String shortCode);
    void delete(String shortCode, String userEmail);
    List<UrlResponse> getUserUrls(String userEmail);

    /**
     * Generate a PNG QR code for the given short code. Returns raw PNG bytes.
     * @param shortCode short code / alias
     * @param size pixel width/height of the square QR image
     */
    byte[] generateQr(String shortCode, int size);
}