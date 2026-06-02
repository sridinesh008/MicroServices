package com.ratelimiter.resolver;

import com.ratelimiter.model.RateLimitKey;
import jakarta.servlet.http.HttpServletRequest;

public interface RateLimitKeyResolver {
    RateLimitKey resolve(HttpServletRequest request);
}
