package com.learn.security.lesson08_custom_filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * LESSON 8 (part 1/2) — YOUR FIRST HAND-WRITTEN SECURITY FILTER.
 *
 * Use case straight from industry: machine-to-machine callers (cron
 * jobs, partner services) authenticate with an API key header instead
 * of username/password:
 *
 *   X-API-KEY: super-secret-key-123
 *
 * BASE CLASS: OncePerRequestFilter — guarantees one execution per
 * request even when the container dispatches internally (forwards,
 * async). Always start custom security filters from it.
 *
 * THE CONTRACT every authentication filter must honor:
 *
 *   1. Look for your credential in the request.
 *   2. ABSENT?   Do nothing. Call chain.doFilter() and step aside —
 *      maybe BasicAuthenticationFilter behind you recognizes the
 *      request. Filters COLLABORATE; each handles only its own scheme.
 *   3. PRESENT + VALID? Build an Authentication object, mark it
 *      authenticated, store it in the SecurityContextHolder. Everything
 *      downstream (AuthorizationFilter, @PreAuthorize, Principal in
 *      controllers) reads from that thread-local — you plugged into the
 *      whole machine with three lines.
 *   4. PRESENT + INVALID? Reject IMMEDIATELY (401), do NOT continue the
 *      chain. A wrong key is an attack signal, not a "maybe".
 *
 * SecurityContextHolder = thread-local holding "who is the current
 * user" for THIS request. Set by auth filters, read by everything else,
 * cleared automatically at request end (SecurityContextHolderFilter).
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-KEY";

    private final String expectedKey;

    /** Key comes from configuration (see the config class) — never hardcode. */
    public ApiKeyFilter(String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String providedKey = request.getHeader(HEADER);

        // (2) no key -> not our scheme -> stand aside
        if (providedKey == null) {
            chain.doFilter(request, response);
            return;
        }

        // (4) wrong key -> reject now, chain stops here
        if (!expectedKey.equals(providedKey)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid API key");
            return;
        }

        // (3) valid key -> authenticate this request as the service account
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "api-client",                                      // principal (who)
                null,                                              // credentials (wiped after auth)
                AuthorityUtils.createAuthorityList("ROLE_API"));   // what they may do
        SecurityContextHolder.getContext().setAuthentication(authentication);

        chain.doFilter(request, response);
    }
}
