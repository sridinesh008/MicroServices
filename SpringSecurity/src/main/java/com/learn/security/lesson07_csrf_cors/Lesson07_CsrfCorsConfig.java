package com.learn.security.lesson07_csrf_cors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * =====================================================================
 *  LESSON 7 — CSRF & CORS: THE TWO BROWSER-ONLY PROBLEMS
 * =====================================================================
 *
 * Run:  mvn spring-boot:run -Dspring-boot.run.profiles=lesson07
 *
 * Both exist ONLY because browsers do things no curl ever does. Both
 * are constantly confused with each other. After this lesson, never again.
 *
 * ---------------------------------------------------------------------
 *  PART 1: CSRF — Cross-Site Request Forgery
 * ---------------------------------------------------------------------
 * THE ATTACK. You are logged into yourbank.com; a session cookie sits
 * in your browser. You visit evil.com, which contains:
 *
 *   <form action="https://yourbank.com/transfer" method="POST">
 *     <input type="hidden" name="to" value="attacker">
 *     <input type="hidden" name="amount" value="10000">
 *   </form>
 *   <script>document.forms[0].submit()</script>
 *
 * The browser AUTOMATICALLY ATTACHES your yourbank.com cookie to that
 * request — cookies go by destination, not by who wrote the HTML. The
 * bank sees a valid session and transfers the money. You clicked nothing.
 *
 * THE DEFENSE. Require state-changing requests (POST/PUT/DELETE) to
 * carry a secret token that evil.com CANNOT read (same-origin policy
 * blocks it from reading yourbank.com responses where the token lives).
 * Cookie arrives automatically; token must be attached deliberately by
 * YOUR page's code. CsrfFilter (chain position 6, Lesson 1) compares.
 * Missing/wrong -> 403 before the controller runs. GETs are exempt —
 * that's why GETs must NEVER change state.
 *
 * WHEN YOU NEED IT — decision rule used in industry:
 *   session cookies for auth?           CSRF protection ON (default)
 *   pure token API (JWT in a header)?   CSRF irrelevant — browsers don't
 *                                       auto-attach headers -> disable it
 *                                       (Lesson 10 does exactly that)
 *
 * SPA VARIANT BELOW: CookieCsrfTokenRepository puts the token in a
 * cookie named XSRF-TOKEN that JavaScript may read (httpOnly=false);
 * Angular/React read it and echo it back in the X-XSRF-TOKEN header.
 * Evil.com still loses: it can trigger the SEND of cookies but cannot
 * READ them cross-origin, so it can't craft the matching header.
 *
 * ---------------------------------------------------------------------
 *  PART 2: CORS — Cross-Origin Resource Sharing
 * ---------------------------------------------------------------------
 * NOT an attack — a browser RESTRICTION you must loosen deliberately.
 * Frontend at http://localhost:3000 calls API at http://localhost:8080:
 * different port = different ORIGIN (origin = scheme + host + port).
 * Browser blocks the JavaScript from reading the response unless YOUR
 * SERVER says that origin is welcome, via response headers:
 *
 *   Access-Control-Allow-Origin: http://localhost:3000
 *
 * For non-simple requests (JSON content type, custom headers, DELETE...)
 * the browser first sends a PREFLIGHT: an OPTIONS request asking "may
 * I?". CorsFilter (chain position 5) answers it from the bean below —
 * preflights never reach your controllers.
 *
 * MENTAL MODEL TO KEEP:
 *   CSRF = browser attaches TOO MUCH automatically (your cookies,
 *          on requests you didn't intend)      -> protect with token
 *   CORS = browser shares TOO LITTLE by default (blocks cross-origin
 *          reads)                              -> open up, precisely
 *
 * PRODUCTION RULES: never allowedOrigins("*") on an API with
 * credentials (Spring refuses the combination anyway); list exact
 * origins; keep the allow-list in config, not code.
 *
 * =====================================================================
 *  TRY IT
 * =====================================================================
 *  A. CSRF blocks a forged-style POST (no token):
 *     curl -i -u alice:alice-pass -X POST http://localhost:8080/transfer
 *     -> 403 — even though valid credentials were attached! Look at the
 *        Lesson 1 chain printout: CsrfFilter (pos 6) runs BEFORE
 *        BasicAuthenticationFilter (pos 11). The request dies before
 *        authentication is even attempted. Filter ORDER is behavior.
 *  B. Legitimate flow (fetch token from cookie, echo in header):
 *     curl -c c.txt -u alice:alice-pass http://localhost:8080/csrf-token
 *     (response prints the token; it is also in cookie XSRF-TOKEN)
 *     curl -b c.txt -u alice:alice-pass -X POST http://localhost:8080/transfer \
 *          -H "X-XSRF-TOKEN: <token from previous response>"
 *     -> 200. Token in header matches cookie -> filter satisfied.
 *  C. CORS preflight — pretend to be a browser at localhost:3000:
 *     curl -i -X OPTIONS http://localhost:8080/public/hello \
 *          -H "Origin: http://localhost:3000" \
 *          -H "Access-Control-Request-Method: GET"
 *     -> 200 + Access-Control-Allow-Origin header. Now retry with
 *        -H "Origin: http://evil.com"  -> 403, origin not on the list.
 *
 * =====================================================================
 *  KEY TAKEAWAYS
 * =====================================================================
 *  - CSRF protects COOKIE-session apps; token APIs disable it (Lesson 10).
 *  - GET must never mutate state — CSRF protection assumes it.
 *  - CORS is server-side permission for browser JS to READ your API
 *    cross-origin; preflight = OPTIONS handled by CorsFilter.
 *  - Exact origin allow-lists. Never * with credentials.
 * =====================================================================
 */
@Configuration
@EnableWebSecurity
@Profile("lesson07")
public class Lesson07_CsrfCorsConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                // GOTCHA worth knowing: Spring Security 6 also secures the
                // ERROR dispatch. When any filter calls sendError(403), the
                // container internally re-dispatches to Boot's /error
                // endpoint — if THAT is locked too, the original 403 mutates
                // into a confusing 401/500. Standard practice: permit /error.
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            // SPA-style CSRF: token readable by JS from cookie XSRF-TOKEN,
            // echoed back in header X-XSRF-TOKEN on writes.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            .cors(Customizer.withDefaults())   // wires the bean below into CorsFilter
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    /** The CORS allow-list. Spring Security picks this bean up by type. */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));  // exact origins only
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);   // allow cookies -> * origins now forbidden
        config.setMaxAge(3600L);            // cache preflight 1h, fewer OPTIONS round-trips

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    UserDetailsService userDetailsService() {
        UserDetails alice = User.builder()
                .username("alice").password("{noop}alice-pass").roles("USER").build();
        return new InMemoryUserDetailsManager(alice);
    }
}
