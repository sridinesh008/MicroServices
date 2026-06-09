package com.ratelimiter.filter;

import com.ratelimiter.config.RateLimiterProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards /api/v1/admin/** with a shared secret in the X-Admin-Key header.
 *
 * Timing-safe comparison (MessageDigest.isEqual) prevents timing attacks —
 * an attacker cannot deduce the key length or prefix by measuring response time.
 *
 * When adminKey is blank (default profile / dev), auth is disabled and a WARN
 * is logged once at startup. Set ADMIN_KEY env var in prod.
 */
@Component
@Order(1)
public class AdminKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminKeyAuthFilter.class);
    private static final String HEADER = "X-Admin-Key";
    private static final String ADMIN_PATTERN = "/api/v1/admin/**";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final byte[] expectedKey;
    private final boolean authDisabled;

    public AdminKeyAuthFilter(RateLimiterProperties properties) {
        String key = properties.getAdminKey();
        if (key == null || key.isBlank()) {
            this.expectedKey  = new byte[0];
            this.authDisabled = true;
            log.warn("[AdminKeyAuthFilter] admin-key is blank — admin endpoints are UNPROTECTED. Set ADMIN_KEY env var in prod.");
        } else {
            this.expectedKey  = key.getBytes(StandardCharsets.UTF_8);
            this.authDisabled = false;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PATH_MATCHER.match(ADMIN_PATTERN, request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (authDisabled) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HEADER);
        if (header != null && timingSafeEquals(header.getBytes(StandardCharsets.UTF_8), expectedKey)) {
            chain.doFilter(request, response);
        } else {
            log.warn("[AdminKeyAuthFilter] Rejected admin request: URI={} remoteAddr={}",
                request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"unauthorized\"}");
        }
    }

    private static boolean timingSafeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }
}
