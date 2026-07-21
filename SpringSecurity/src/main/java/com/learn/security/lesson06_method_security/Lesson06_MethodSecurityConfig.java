package com.learn.security.lesson06_method_security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * =====================================================================
 *  LESSON 6 — METHOD SECURITY: THE SECOND LINE OF DEFENSE
 * =====================================================================
 *
 * Run:  mvn spring-boot:run -Dspring-boot.run.profiles=lesson06
 *
 * WHY URL RULES ARE NOT ENOUGH
 * ----------------------------
 * URL rules protect ROUTES. But the same service method is often
 * reachable through many routes (REST controller, GraphQL, scheduled
 * job, message listener, another service calling it directly). And URL
 * rules can't express DATA-level decisions like "you may read this
 * document only if you OWN it" — the URL doesn't know who owns what.
 *
 * Industry practice = DEFENSE IN DEPTH:
 *   coarse rules at the edge  (URL:    "/documents/** -> authenticated")
 *   precise rules at the core (method: "only the owner or an admin")
 *
 * HOW IT WORKS
 * ------------
 * @EnableMethodSecurity (on this class) makes Spring wrap annotated
 * beans in a proxy. Calling the method first runs the security check —
 * fails -> AccessDeniedException -> 403, method body never executes.
 * Same mechanism as @Transactional. Same caveat too: calls from INSIDE
 * the same class bypass the proxy — annotations on private/self-invoked
 * methods silently DO NOTHING. Classic prod bug, see DocumentService.
 *
 * THE ANNOTATIONS (all SpEL — Spring Expression Language)
 * -------------------------------------------------------
 *   @PreAuthorize("hasRole('ADMIN')")            gate before the call
 *   @PreAuthorize("#owner == authentication.name")
 *        `#owner`            = the method PARAMETER named owner
 *        `authentication`    = current logged-in Authentication
 *        -> "callers may only pass their OWN username"
 *   @PostAuthorize("returnObject.owner == authentication.name")
 *        gate AFTER the call, inspecting the RETURN VALUE — for when you
 *        must load the object before knowing who owns it
 *   @PreFilter / @PostFilter  filter collections element-by-element
 *        (use sparingly: filtering a 10k-element list in Java means you
 *        should have filtered in the SQL instead)
 *
 * All lesson-3 knowledge applies: hasRole adds ROLE_ prefix, the
 * RoleHierarchy bean (if defined) is honored here too.
 *
 * =====================================================================
 *  TRY IT   (users: alice/alice-pass USER, bob/bob-pass ADMIN)
 * =====================================================================
 *  A. alice reads HER OWN documents — parameter check passes:
 *     curl -i -u alice:alice-pass "http://localhost:8080/documents?owner=alice"
 *     -> 200
 *  B. alice tries to read BOB's documents:
 *     curl -i -u alice:alice-pass "http://localhost:8080/documents?owner=bob"
 *     -> 403. URL rule said "authenticated" (she is!) — the METHOD said no.
 *        This is the granularity URL rules cannot reach.
 *  C. bob the admin reads alice's documents:
 *     curl -i -u bob:bob-pass "http://localhost:8080/documents?owner=alice"
 *     -> 200 (the SpEL has `or hasRole('ADMIN')`).
 *  D. alice fetches doc 1 (owned by alice) vs doc 2 (owned by bob):
 *     curl -i -u alice:alice-pass http://localhost:8080/documents/1  -> 200
 *     curl -i -u alice:alice-pass http://localhost:8080/documents/2  -> 403
 *     Same URL shape, same role — the RETURN VALUE decided (@PostAuthorize).
 *  E. only admin deletes:
 *     curl -i -u alice:alice-pass -X DELETE http://localhost:8080/documents/1 -> 403
 *     curl -i -u bob:bob-pass   -X DELETE http://localhost:8080/documents/1 -> 200
 *
 * =====================================================================
 *  KEY TAKEAWAYS
 * =====================================================================
 *  - URL rules = coarse edge control; method rules = precise, travel
 *    with the code, cover non-HTTP entry points.
 *  - #param / authentication / returnObject in SpEL cover 95% of needs.
 *  - Proxy-based: self-invocation bypasses checks. Annotate PUBLIC
 *    methods called from OUTSIDE the class only.
 *  - Still return 403 via ExceptionTranslationFilter — same machinery.
 * =====================================================================
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // <- the switch. Without it every annotation below is dead text.
@Profile("lesson06")
public class Lesson06_MethodSecurityConfig {

    /**
     * Deliberately COARSE: any authenticated user may reach /documents/**.
     * The precision lives in DocumentService. Delete the method
     * annotations and B/D/E above all become 200 — try it, feel the hole.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/documents/**", "/documents").authenticated()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/private/**").hasRole("USER")
                .anyRequest().denyAll()
            )
            // DELETE needs a CSRF token by default; for this API lesson we
            // exempt /documents/**. Full CSRF story is Lesson 7 — peek ahead
            // if this line bothers you (it should).
            .csrf(csrf -> csrf.ignoringRequestMatchers("/documents/**"))
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        UserDetails alice = User.builder()
                .username("alice").password("{noop}alice-pass").roles("USER").build();
        UserDetails bob = User.builder()
                .username("bob").password("{noop}bob-pass").roles("USER", "ADMIN").build();
        return new InMemoryUserDetailsManager(alice, bob);
    }
}
