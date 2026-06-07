package com.ratelimiter.model;

/**
 * Standard JSON error shape returned by GlobalExceptionHandler for all controller exceptions.
 * Example: {"status":404,"error":"Not Found","message":"Resource not found","path":"/api/rules/x"}
 */
public record ErrorResponse(
    int    status,
    String error,
    String message,
    String path
) {}
