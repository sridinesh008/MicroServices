package com.ratelimiter.resolver;

import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitScope;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EndpointKeyResolver implements RateLimitKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(EndpointKeyResolver.class);

    @Override
    public RateLimitKey resolve(HttpServletRequest request) {
        log.debug("[EndpointKeyResolver] Resolving key for URI={} method={}", request.getRequestURI(), request.getMethod());
        // clientId = "GET:/api/users" — method+URI uniquely identifies an endpoint for rate limiting
        String clientId = request.getMethod() + ":" + request.getRequestURI();
        RateLimitKey key = new RateLimitKey(RateLimitScope.ENDPOINT, clientId, request.getRequestURI());
        log.info("[EndpointKeyResolver] Resolved → scope={} clientId={}", key.scope(), key.clientId());
        return key;
    }
}
