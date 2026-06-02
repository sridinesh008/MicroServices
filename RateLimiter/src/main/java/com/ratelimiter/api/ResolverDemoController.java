package com.ratelimiter.api;

import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.resolver.EndpointKeyResolver;
import com.ratelimiter.resolver.IpKeyResolver;
import com.ratelimiter.resolver.UserIdKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Demo-only controller. Remove after Phase 16.
 * Exposes resolver results so curl tests can verify behaviour.
 */
@RestController
@RequestMapping("/api/v1/demo")
public class ResolverDemoController {

    private final IpKeyResolver       ipResolver;
    private final UserIdKeyResolver   userResolver;
    private final EndpointKeyResolver endpointResolver;

    public ResolverDemoController(IpKeyResolver ip, UserIdKeyResolver user, EndpointKeyResolver endpoint) {
        this.ipResolver       = ip;
        this.userResolver     = user;
        this.endpointResolver = endpoint;
    }

    /** Test 1 — IP resolver: reads X-Forwarded-For or falls back to remoteAddr */
    @RequestMapping("/resolve/ip")
    public RateLimitKey resolveIp(HttpServletRequest request) {
        return ipResolver.resolve(request);
    }

    /** Test 2 — User resolver: reads Authorization: Bearer <JWT>, extracts sub claim */
    @RequestMapping("/resolve/user")
    public RateLimitKey resolveUser(HttpServletRequest request) {
        return userResolver.resolve(request);
    }

    /** Test 3 — Endpoint resolver: clientId = METHOD:URI */
    @RequestMapping("/resolve/endpoint")
    public Map<String, Object> resolveEndpoint(HttpServletRequest request) {
        RateLimitKey key = endpointResolver.resolve(request);
        return Map.of(
            "key",    key,
            "method", request.getMethod(),
            "uri",    request.getRequestURI()
        );
    }
}
