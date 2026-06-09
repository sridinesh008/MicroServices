package com.ratelimiter.resolver;

import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitScope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Combines IP address and user ID into a single composite key.
 * Enforces a per-(IP, user) limit — prevents a single user from bypassing
 * IP limits via multiple accounts, and prevents IP-sharing scenarios from
 * collapsing all users onto one bucket.
 *
 * Key format: "{ip}|{userId}"
 * Example: "10.0.0.1|user-123"
 */
@Component
public class CompositeKeyResolver implements RateLimitKeyResolver {

    private final IpKeyResolver ipResolver;
    private final UserIdKeyResolver userIdResolver;

    public CompositeKeyResolver(IpKeyResolver ipResolver, UserIdKeyResolver userIdResolver) {
        this.ipResolver = ipResolver;
        this.userIdResolver = userIdResolver;
    }

    @Override
    public RateLimitKey resolve(HttpServletRequest request) {
        String ip     = ipResolver.resolve(request).clientId();
        String userId = userIdResolver.resolve(request).clientId();
        return new RateLimitKey(RateLimitScope.COMPOSITE, ip + "|" + userId, request.getRequestURI());
    }
}
