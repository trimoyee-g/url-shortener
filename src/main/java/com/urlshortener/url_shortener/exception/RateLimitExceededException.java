package com.urlshortener.url_shortener.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String ip) {
        super("Rate limit exceeded for: " + ip);
    }
}
