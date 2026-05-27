package com.urlshortener.url_shortener.exception;

/**
 * Thrown by RedirectController when a short link requires a password
 * and none was supplied (or the supplied one was wrong).
 */
public class PasswordRequiredException extends RuntimeException {

    private final String shortCode;

    public PasswordRequiredException(String shortCode) {
        super("Password required for: " + shortCode);
        this.shortCode = shortCode;
    }

    public String getShortCode() {
        return shortCode;
    }
}
