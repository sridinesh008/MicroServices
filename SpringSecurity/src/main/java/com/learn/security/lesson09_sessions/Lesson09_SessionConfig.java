package com.learn.security.lesson09_sessions;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * =====================================================================
 *  LESSON 9 — SESSIONS: WHAT KEEPS YOU LOGGED IN, AND ITS DARK SIDE
 * =====================================================================
 *
 * Run:  mvn spring-boot:run -Dspring-boot.run.profiles=lesson09
 *
 * WHAT A SESSION ACTUALLY IS
 * --------------------------
 * HTTP is stateless — the server forgets you after every request. To
 * "stay logged in", after a successful login the server:
 *   1. creates a server-side storage slot (the HttpSession) and puts
 *      your SecurityContext (who you are) in it
 *   2. hands the browser a cookie: JSESSIONID=<random id>
 * Every later request carries the cookie; SecurityContextHolderFilter
 * (Lesson 1, position 3) uses it to restore "who you are". The COOKIE
 * IS YOUR IDENTITY now — whoever holds it, IS you. Every session attack
 * follows from that sentence.
 *
 * ATTACK 1: SESSION FIXATION
 * --------------------------
 * Attacker gets a session id FIRST (visits your site, gets JSESSIONID=X),
 * then plants X into the victim's browser (link with ;jsessionid=X, or
 * subdomain cookie injection). Victim logs in — if the server KEEPS
 * session X, the attacker's copy of X is now an authenticated session.
 * DEFENSE: swap the session id at the moment of login. Spring Security
 * does this BY DEFAULT (changeSessionId strategy) — you have been
 * protected in every lesson without knowing. The config below only
 * makes the default visible.
 *
 * ATTACK 2: SESSION HIJACKING
 * ---------------------------
 * Steal the cookie itself: sniff plain HTTP, or read it from JS after an
 * XSS. Defenses live on the COOKIE ATTRIBUTES (set in application.yml
 * for this lesson — go read that block now):
 *   HttpOnly  JS cannot read the cookie -> XSS can't exfiltrate it
 *   Secure    cookie only travels over HTTPS -> nothing to sniff
 *   SameSite=Lax  cookie not attached to most cross-site requests
 *                 (also your CSRF backup line from Lesson 7)
 *
 * CONCURRENT SESSION CONTROL
 * --------------------------
 * maximumSessions(1): each login kills the previous session (or, with
 * maxSessionsPreventsLogin(true), blocks the new login instead — banks
 * do that). Needs two extra pieces, both below: a SessionRegistry (who
 * has which session) and HttpSessionEventPublisher (tells the registry
 * when sessions die, so slots free up).
 *
 * THE POLICY LADDER (sessionCreationPolicy)
 * -----------------------------------------
 *   ALWAYS      create session for every request       (rare)
 *   IF_REQUIRED create when something needs storing    (default)
 *   NEVER       never CREATE, but USE one if it exists
 *   STATELESS   never create, never use — every request must carry its
 *               own credentials (API key L8, JWT L10). No session, no
 *               fixation, no hijacking, no CSRF — whole attack class
 *               deleted. Price: every request re-proves identity, and
 *               logout/revocation becomes YOUR problem (Lesson 10).
 *
 * =====================================================================
 *  TRY IT
 * =====================================================================
 *  A. See the session get SWAPPED at login (fixation defense):
 *     1. curl -i -c pre.txt http://localhost:8080/login
 *        note JSESSIONID value (anonymous session)
 *     2. log in via browser devtools (Network tab) at /login as
 *        alice/alice-pass — watch Set-Cookie: JSESSIONID change to a NEW
 *        value the moment login succeeds.
 *  B. Concurrent control — log in as alice in TWO browsers (or normal +
 *     incognito). Refresh the first: session expired message. Second
 *     login evicted the first (maximumSessions(1)).
 *  C. Cookie attributes: browser devtools -> Application -> Cookies ->
 *     JSESSIONID: HttpOnly ✓, SameSite Lax (from application.yml).
 *
 * =====================================================================
 *  KEY TAKEAWAYS
 * =====================================================================
 *  - Session cookie = bearer of your identity; protect it like a password.
 *  - Fixation: defended by default (session id swap at login).
 *  - Hijacking: HttpOnly + Secure + SameSite cookie attributes.
 *  - maximumSessions for eviction; needs registry + event publisher.
 *  - STATELESS deletes session attacks entirely — the API world's choice.
 * =====================================================================
 */
@Configuration
@EnableWebSecurity
@Profile("lesson09")
public class Lesson09_SessionConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SessionRegistry registry) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                // the DEFAULT, written out so you see where it lives:
                .sessionFixation(fixation -> fixation.changeSessionId())
                // one active session per user; new login evicts the old
                .maximumSessions(1)
                .sessionRegistry(registry)
                .expiredUrl("/login?expired")   // where the evicted browser lands
            )
            .formLogin(Customizer.withDefaults())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    /** Who currently holds which session — required by maximumSessions. */
    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Forwards servlet session-destroyed events to the registry. */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    UserDetailsService userDetailsService() {
        UserDetails alice = User.builder()
                .username("alice").password("{noop}alice-pass").roles("USER").build();
        return new InMemoryUserDetailsManager(alice);
    }
}
