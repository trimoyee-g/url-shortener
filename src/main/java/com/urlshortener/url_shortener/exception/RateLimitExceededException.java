package com.urlshortener.url_shortener.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String key) {
        super("Rate limit exceeded for: " + key);
    }
}
