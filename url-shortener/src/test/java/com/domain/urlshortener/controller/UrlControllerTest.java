package com.domain.urlshortener.controller;

import com.domain.urlshortener.dto.ShortenRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UrlControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private StringRedisTemplate redisTemplate;

    @Test
    void shorten_validUrl_returns201WithShortCode() throws Exception {
        mockRedis();

        ShortenRequest request = new ShortenRequest();
        request.setUrl("https://www.example.com/some/very/long/url/path");

        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.shortUrl").value(org.hamcrest.Matchers.startsWith("http://localhost:8080/")))
                .andExpect(jsonPath("$.originalUrl").value("https://www.example.com/some/very/long/url/path"));
    }

    @Test
    void shorten_invalidUrl_returns400() throws Exception {
        ShortenRequest request = new ShortenRequest();
        request.setUrl("not-a-url");

        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redirect_validCode_returns301() throws Exception {
        mockRedis();

        ShortenRequest request = new ShortenRequest();
        request.setUrl("https://www.google.com");
        MvcResult result = mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String shortCode = objectMapper.readTree(body).get("shortCode").asText();

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "https://www.google.com"));
    }

    @Test
    void redirect_unknownCode_returns404() throws Exception {
        mockRedis();

        mockMvc.perform(get("/doesnotexist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void stats_validCode_returnsClickCount() throws Exception {
        mockRedis();

        ShortenRequest request = new ShortenRequest();
        request.setUrl("https://www.stats-test.com");
        MvcResult result = mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        String shortCode = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("shortCode").asText();

        mockMvc.perform(get("/api/stats/" + shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(0));
    }

    private void mockRedis() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
    }
}
