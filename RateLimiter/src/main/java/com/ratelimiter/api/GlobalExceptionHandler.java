package com.ratelimiter.api;

import com.ratelimiter.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles ResponseStatusException thrown by controllers.
     * Status and reason come from the exception itself — caller controls the HTTP code.
     * Example: throw new ResponseStatusException(404, "Rule not found") →
     *   {"status":404,"error":"Not Found","message":"Rule not found","path":"/api/rules/x"}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + " " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        log.warn("[GlobalExceptionHandler] Validation failed path={} errors={}", request.getRequestURI(), message);
        return ResponseEntity.status(400).body(new ErrorResponse(400, "Bad Request", message, request.getRequestURI()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {

        int    code    = ex.getStatusCode().value();
        String reason  = ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString();

        log.warn("[GlobalExceptionHandler] ResponseStatusException status={} path={} reason={}",
            code, request.getRequestURI(), reason);

        return ResponseEntity.status(code).body(new ErrorResponse(
            code,
            reason,
            reason,
            request.getRequestURI()
        ));
    }

    /**
     * Catch-all for any unhandled exception from a controller.
     * Returns 500 with a safe generic message — never leaks stack trace or internal detail.
     * Example: NullPointerException in AdminController →
     *   {"status":500,"error":"Internal Server Error","message":"An unexpected error occurred","path":"/api/rules"}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("[GlobalExceptionHandler] Unhandled exception at path={}", request.getRequestURI(), ex);

        return ResponseEntity.status(500).body(new ErrorResponse(
            500,
            "Internal Server Error",
            "An unexpected error occurred",
            request.getRequestURI()
        ));
    }
}
