package com.ratelimiter.resolver;

import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitScope;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IpKeyResolver implements RateLimitKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(IpKeyResolver.class);
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    @Override
    public RateLimitKey resolve(HttpServletRequest request) {
        log.debug("[IpKeyResolver] Resolving key for URI={} method={}", request.getRequestURI(), request.getMethod());
        RateLimitKey key = new RateLimitKey(RateLimitScope.IP, extractIp(request), request.getRequestURI());
        log.info("[IpKeyResolver] Resolved → scope={} clientId={}", key.scope(), key.clientId());
        return key;
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader(X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For: "203.0.113.1, 70.41.3.18" → first entry = original client
            String ip = forwarded.split(",")[0].trim();
            log.debug("[IpKeyResolver] X-Forwarded-For header found: '{}' → using first IP: {}", forwarded, ip);
            return ip;
        }
        log.debug("[IpKeyResolver] No X-Forwarded-For header → using remoteAddr: {}", request.getRemoteAddr());
        return request.getRemoteAddr();
    }
}
