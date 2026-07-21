package com.learn.security.lesson12_oauth2_server;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LESSON 12 — the protected resources + the redirect landing spot.
 */
@RestController
@Profile("lesson12")
public class ScopedApiController {

    /** Needs SCOPE_api.read. Principal name = "sub" claim of the token. */
    @GetMapping("/api/data")
    public String read(Authentication authentication) {
        return "read OK for " + authentication.getName()
                + " with authorities " + authentication.getAuthorities();
    }

    /** Needs SCOPE_api.write — TRY IT (E) proves scopes bite. */
    @PostMapping("/api/write")
    public String write(Authentication authentication) {
        return "write OK for " + authentication.getName();
    }

    /**
     * Redirect target for the authorization-code flow (TRY IT F).
     * Real apps: this is where the backend grabs the code and exchanges
     * it server-side. Here we just show it so you can curl the exchange
     * yourself and SEE step 4 of Lesson 11's flow.
     */
    @GetMapping("/public/callback")
    public String callback(@RequestParam String code) {
        return "authorization code received:\n" + code
                + "\n\nNow exchange it (TRY IT F step 4).";
    }
}
