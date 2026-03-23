package com.domain.urlshortener.exception;

public class UrlNotFoundException extends RuntimeException {
    public UrlNotFoundException(String shortCode) {
        super("No active URL found for code: " + shortCode);
    }
}
