package com.domain.urlshortener.service;

import com.domain.urlshortener.dto.ShortenRequest;
import com.domain.urlshortener.dto.ShortenResponse;
import com.domain.urlshortener.dto.StatsResponse;
import com.domain.urlshortener.exception.AliasAlreadyExistsException;
import com.domain.urlshortener.exception.UrlNotFoundException;
import com.domain.urlshortener.model.UrlMapping;
import com.domain.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlService {

    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_RETRIES = 5;
    private static final String CACHE_PREFIX = "url:";

    private final UrlRepository urlRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.cache.ttl-hours:24}")
    private long cacheTtlHours;

    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        String code = resolveCode(request);
        LocalDateTime expiresAt = request.getTtlDays() != null
                ? LocalDateTime.now().plusDays(request.getTtlDays())
                : null;

        UrlMapping mapping = UrlMapping.builder()
                .shortCode(code)
                .originalUrl(request.getUrl())
                .expiresAt(expiresAt)
                .build();

        urlRepository.save(mapping);
        cacheUrl(code, request.getUrl());

        log.info("Shortened {} -> {}", request.getUrl(), code);

        return ShortenResponse.builder()
                .shortCode(code)
                .shortUrl(baseUrl + "/" + code)
                .originalUrl(request.getUrl())
                .createdAt(mapping.getCreatedAt())
                .expiresAt(expiresAt)
                .build();
    }

    @Transactional
    public String resolve(String shortCode) {
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + shortCode);
        if (cached != null) {
            log.debug("Cache hit for {}", shortCode);
            urlRepository.incrementClickCount(shortCode);
            return cached;
        }

        log.debug("Cache miss for {}, querying DB", shortCode);
        UrlMapping mapping = urlRepository
                .findActiveByShortCode(shortCode, LocalDateTime.now())
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        cacheUrl(shortCode, mapping.getOriginalUrl());
        urlRepository.incrementClickCount(shortCode);

        return mapping.getOriginalUrl();
    }

    @Transactional(readOnly = true)
    public StatsResponse stats(String shortCode) {
        UrlMapping mapping = urlRepository
                .findActiveByShortCode(shortCode, LocalDateTime.now())
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        return StatsResponse.builder()
                .shortCode(mapping.getShortCode())
                .originalUrl(mapping.getOriginalUrl())
                .clickCount(mapping.getClickCount())
                .createdAt(mapping.getCreatedAt())
                .expiresAt(mapping.getExpiresAt())
                .build();
    }

    private String resolveCode(ShortenRequest request) {
        if (request.getCustomAlias() != null && !request.getCustomAlias().isBlank()) {
            if (urlRepository.existsByShortCode(request.getCustomAlias())) {
                throw new AliasAlreadyExistsException(request.getCustomAlias());
            }
            return request.getCustomAlias();
        }
        return generateUniqueCode();
    }

    private String generateUniqueCode() {
        Random random = new Random();
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String code = generateCode(random);
            if (!urlRepository.existsByShortCode(code)) {
                return code;
            }
            log.warn("Short code collision on attempt {}: {}", attempt + 1, code);
        }
        throw new IllegalStateException("Could not generate a unique short code after " + MAX_RETRIES + " attempts");
    }

    private String generateCode(Random random) {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(BASE62.charAt(random.nextInt(BASE62.length())));
        }
        return sb.toString();
    }

    private void cacheUrl(String code, String originalUrl) {
        redisTemplate.opsForValue().set(CACHE_PREFIX + code, originalUrl, cacheTtlHours, TimeUnit.HOURS);
    }
}
