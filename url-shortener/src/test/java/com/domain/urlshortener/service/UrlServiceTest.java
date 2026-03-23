package com.domain.urlshortener.service;

import com.domain.urlshortener.dto.ShortenRequest;
import com.domain.urlshortener.dto.ShortenResponse;
import com.domain.urlshortener.exception.AliasAlreadyExistsException;
import com.domain.urlshortener.exception.UrlNotFoundException;
import com.domain.urlshortener.model.UrlMapping;
import com.domain.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock private UrlRepository urlRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private UrlService urlService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urlService, "baseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(urlService, "cacheTtlHours", 24L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void shorten_generatesShortCode_andCachesIt() {
        ShortenRequest request = new ShortenRequest();
        request.setUrl("https://www.example.com/some/long/path");

        when(urlRepository.existsByShortCode(anyString())).thenReturn(false);
        when(urlRepository.save(any())).thenAnswer(inv -> {
            UrlMapping m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "createdAt", LocalDateTime.now());
            return m;
        });

        ShortenResponse response = urlService.shorten(request);

        assertThat(response.getShortCode()).hasSize(6);
        assertThat(response.getShortUrl()).startsWith("http://localhost:8080/");
        assertThat(response.getOriginalUrl()).isEqualTo("https://www.example.com/some/long/path");
        verify(valueOps).set(eq("url:" + response.getShortCode()), eq(request.getUrl()), anyLong(), any());
    }

    @Test
    void shorten_withCustomAlias_usesAlias() {
        ShortenRequest request = new ShortenRequest();
        request.setUrl("https://example.com");
        request.setCustomAlias("my-blog");

        when(urlRepository.existsByShortCode("my-blog")).thenReturn(false);
        when(urlRepository.save(any())).thenAnswer(inv -> {
            UrlMapping m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "createdAt", LocalDateTime.now());
            return m;
        });

        ShortenResponse response = urlService.shorten(request);

        assertThat(response.getShortCode()).isEqualTo("my-blog");
    }

    @Test
    void shorten_withTakenAlias_throwsConflict() {
        ShortenRequest request = new ShortenRequest();
        request.setUrl("https://example.com");
        request.setCustomAlias("taken");

        when(urlRepository.existsByShortCode("taken")).thenReturn(true);

        assertThatThrownBy(() -> urlService.shorten(request))
                .isInstanceOf(AliasAlreadyExistsException.class);
    }

    @Test
    void resolve_cacheHit_returnsWithoutHittingDB() {
        when(valueOps.get("url:abc123")).thenReturn("https://example.com");

        String result = urlService.resolve("abc123");

        assertThat(result).isEqualTo("https://example.com");
        verify(urlRepository, never()).findActiveByShortCode(any(), any());
        verify(urlRepository).incrementClickCount("abc123");
    }

    @Test
    void resolve_cacheMiss_queriesDB_andCaches() {
        when(valueOps.get("url:abc123")).thenReturn(null);
        UrlMapping mapping = UrlMapping.builder()
                .shortCode("abc123")
                .originalUrl("https://example.com")
                .clickCount(5L)
                .build();
        when(urlRepository.findActiveByShortCode(eq("abc123"), any())).thenReturn(Optional.of(mapping));

        String result = urlService.resolve("abc123");

        assertThat(result).isEqualTo("https://example.com");
        verify(valueOps).set(eq("url:abc123"), eq("https://example.com"), anyLong(), any());
        verify(urlRepository).incrementClickCount("abc123");
    }

    @Test
    void resolve_notFound_throwsException() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(urlRepository.findActiveByShortCode(anyString(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.resolve("ghost"))
                .isInstanceOf(UrlNotFoundException.class)
                .hasMessageContaining("ghost");
    }
}
