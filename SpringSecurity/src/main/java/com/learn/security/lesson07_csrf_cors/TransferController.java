package com.learn.security.lesson07_csrf_cors;

import org.springframework.context.annotation.Profile;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * LESSON 7 — victim endpoints for the CSRF demo.
 */
@RestController
@Profile("lesson07")
public class TransferController {

    /**
     * State-changing endpoint — exactly what CSRF attacks target.
     * Note: still no security code in the controller. CsrfFilter already
     * rejected token-less POSTs before we got here.
     */
    @PostMapping("/transfer")
    public String transfer(Principal principal) {
        return "TRANSFER EXECUTED for " + principal.getName()
                + " — this line only runs when the CSRF token was valid";
    }

    /**
     * Helper for the curl exercise: forces the deferred token to be
     * generated and shows it. (Browser apps read the XSRF-TOKEN cookie
     * instead — they never need an endpoint like this.)
     */
    @GetMapping("/csrf-token")
    public String csrfToken(CsrfToken token) {
        return "header name: " + token.getHeaderName() + "\ntoken: " + token.getToken();
    }
}
