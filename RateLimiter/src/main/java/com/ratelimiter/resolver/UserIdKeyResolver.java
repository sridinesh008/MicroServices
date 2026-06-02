package com.ratelimiter.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitScope;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class UserIdKeyResolver implements RateLimitKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(UserIdKeyResolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public RateLimitKey resolve(HttpServletRequest request) {
        log.debug("[UserIdKeyResolver] Resolving key for URI={} method={}", request.getRequestURI(), request.getMethod());
        RateLimitKey key = new RateLimitKey(RateLimitScope.USER_ID, extractUserId(request), request.getRequestURI());
        log.info("[UserIdKeyResolver] Resolved → scope={} clientId={}", key.scope(), key.clientId());
        return key;
    }

    private String extractUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            log.debug("[UserIdKeyResolver] No Bearer token → fallback to remoteAddr={}", request.getRemoteAddr());
            return request.getRemoteAddr();
        }
        log.debug("[UserIdKeyResolver] Bearer token found, parsing JWT payload");
        try {
            String[] parts = auth.substring(7).split("\\.");
            if (parts.length < 2) {
                log.warn("[UserIdKeyResolver] JWT has fewer than 2 parts → fallback to IP");
                return request.getRemoteAddr();
            }

            // JWT payload = parts[1], base64url-encoded
            // Example: eyJzdWIiOiJ1c2VyMTIzIn0 → {"sub":"user123"}
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            JsonNode payload = MAPPER.readTree(decoded);
            log.debug("[UserIdKeyResolver] JWT payload decoded: {}", payload);

            JsonNode sub = payload.get("sub");
            if (sub != null && !sub.isNull()) {
                log.debug("[UserIdKeyResolver] Found sub claim: {}", sub.asText());
                return sub.asText();
            }
            log.warn("[UserIdKeyResolver] JWT has no 'sub' claim → fallback to IP");
        } catch (IllegalArgumentException | java.io.IOException e) {
            log.warn("[UserIdKeyResolver] JWT parse failed → fallback to IP. Reason: {}", e.getMessage());
        }
        return request.getRemoteAddr();
    }

    private String padBase64(String s) {
        // base64url omits '=' padding — restore to make length a multiple of 4
        // Example: len=5 → pad=3 → "abcde===" (len=8)
        int pad = (4 - s.length() % 4) % 4;
        return s + "=".repeat(pad);
    }
}
