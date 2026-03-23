package com.domain.urlshortener.controller;

import com.domain.urlshortener.dto.ShortenRequest;
import com.domain.urlshortener.dto.ShortenResponse;
import com.domain.urlshortener.dto.StatsResponse;
import com.domain.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;

    @PostMapping("/api/shorten")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortenResponse response = urlService.shorten(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String originalUrl = urlService.resolve(shortCode);
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, originalUrl)
                .build();
    }

    @GetMapping("/api/stats/{shortCode}")
    public ResponseEntity<StatsResponse> stats(@PathVariable String shortCode) {
        return ResponseEntity.ok(urlService.stats(shortCode));
    }
}
