package com.urlshortener.url_shortener.exception;

public class AliasAlreadyExistsException extends RuntimeException {
    public AliasAlreadyExistsException(String alias) {
        super("Custom alias already taken: " + alias);
    }
}
