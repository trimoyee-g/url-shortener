package com.urlshortener.url_shortener.exception;

public class AlreadyExistsException extends RuntimeException {
    public AlreadyExistsException(String alias) {
        super(alias);
    }
}
