package com.learn.security.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * SHARED TEST ENDPOINTS — used by every lesson.
 *
 * Three endpoints with three different *intended* protection levels.
 * Note: this controller contains NO security logic at all. In Spring
 * Security, protection is applied OUTSIDE the controller, by a chain of
 * servlet filters that run BEFORE the request ever reaches this class.
 *
 * That is the first big mental model of the course:
 *
 *   Browser -> [Security Filter Chain] -> DispatcherServlet -> Controller
 *
 * If a filter rejects the request (401/403), the controller never runs.
 */
@RestController
public class HelloController {

    /** Intended: anyone can call this, no login. */
    @GetMapping("/public/hello")
    public ResponseEntity<String> publicHello() {
        return ResponseEntity.ok("Hello from PUBLIC endpoint - no login should be needed");
    }

    /**
     * Intended: any logged-in user.
     *
     * The `Principal` parameter is injected by Spring: after authentication
     * succeeds, the filter chain stores WHO you are in the SecurityContext,
     * and Spring MVC can hand it to controller methods on demand.
     * If nobody is logged in, principal is null.
     */
    @GetMapping("/private/hello")
    public ResponseEntity<String> privateHello(Principal principal) {
        String who = (principal != null) ? principal.getName() : "anonymous";
        return ResponseEntity.ok("Hello " + who + " from PRIVATE endpoint - login required");
    }

    /** Intended: only users with the ADMIN role. */
    @GetMapping("/admin/hello")
    public ResponseEntity<String> adminHello(Principal principal) {
        String who = (principal != null) ? principal.getName() : "anonymous";
        return ResponseEntity.ok("Hello " + who + " from ADMIN endpoint - ADMIN role required");
    }
}
