package com.ratelimiter.filter;

import com.ratelimiter.limiter.RateLimiter;
import com.ratelimiter.model.RateLimitKey;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.resolver.EndpointKeyResolver;
import com.ratelimiter.resolver.IpKeyResolver;
import com.ratelimiter.resolver.UserIdKeyResolver;
import com.ratelimiter.rule.RuleMatchingService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    // Requests matching these patterns skip rate limiting entirely
    private static final List<String> BYPASS_PATTERNS = List.of(
        "/actuator/**",
        "/api/v1/admin/**"
    );

    private final RuleMatchingService ruleMatchingService;
    private final RateLimiter rateLimiter;
    private final IpKeyResolver ipResolver;
    private final UserIdKeyResolver userResolver;
    private final EndpointKeyResolver endpointResolver;
    private final MeterRegistry meterRegistry;

    public RateLimitFilter(RuleMatchingService ruleMatchingService,
                           RateLimiter rateLimiter,
                           IpKeyResolver ipResolver,
                           UserIdKeyResolver userResolver,
                           EndpointKeyResolver endpointResolver,
                           MeterRegistry meterRegistry) {
        this.ruleMatchingService = ruleMatchingService;
        this.rateLimiter         = rateLimiter;
        this.ipResolver          = ipResolver;
        this.userResolver        = userResolver;
        this.endpointResolver    = endpointResolver;
        this.meterRegistry       = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        if (isBypassed(uri)) {
            log.debug("[RateLimitFilter] Bypassed URI={}", uri);
            chain.doFilter(request, response);
            return;
        }

        Optional<RateLimitRule> ruleOpt = ruleMatchingService.findBestMatch(request);
        if (ruleOpt.isEmpty()) {
            log.debug("[RateLimitFilter] No rule matched URI={} → fail-open", uri);
            chain.doFilter(request, response);
            return;
        }

        RateLimitRule rule = ruleOpt.get();
        RateLimitKey  key  = resolveKey(request, rule);
        log.debug("[RateLimitFilter] Checking rule={} scope={} clientId={}", rule.ruleId(), rule.scope(), key.clientId());

        try {
            RateLimitResult result = rateLimiter.checkLimit(key, rule);

            // Always write headers so client knows current limit state
            response.setHeader("X-RateLimit-Limit",     String.valueOf(result.limitCapacity()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));
            response.setHeader("X-RateLimit-Reset",     String.valueOf(result.resetAtEpochSeconds()));

            if (result.allowed()) {
                meterRegistry.counter("rate_limit_allowed_total",
                    "rule", rule.ruleId(), "scope", rule.scope().name()).increment();
                log.info("[RateLimitFilter] ALLOWED URI={} clientId={} remaining={}", uri, key.clientId(), result.remainingTokens());
                chain.doFilter(request, response);
            } else {
                meterRegistry.counter("rate_limit_denied_total",
                    "rule", rule.ruleId(), "scope", rule.scope().name()).increment();
                log.info("[RateLimitFilter] DENIED  URI={} clientId={} retryAfter={}s", uri, key.clientId(), result.retryAfterSeconds());
                response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write(String.format(
                    "{\"error\":\"rate_limit_exceeded\",\"retryAfter\":%d}",
                    result.retryAfterSeconds()
                ));
            }
        } catch (Exception e) {
            // Fail-open: store errors never block traffic, but always log
            log.warn("[RateLimitFilter] Store error → fail-open. URI={} reason={}", uri, e.getMessage());
            chain.doFilter(request, response);
        }
    }

    private boolean isBypassed(String uri) {
        return BYPASS_PATTERNS.stream().anyMatch(p -> PATH_MATCHER.match(p, uri));
    }

    private RateLimitKey resolveKey(HttpServletRequest request, RateLimitRule rule) {
        // Pick resolver based on rule scope so key type matches intent
        return switch (rule.scope()) {
            case USER_ID  -> userResolver.resolve(request);
            case ENDPOINT -> endpointResolver.resolve(request);
            default       -> ipResolver.resolve(request); // IP + COMPOSITE
        };
    }
}
