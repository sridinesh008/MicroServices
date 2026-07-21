package com.learn.security.lesson13_production;

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
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * =====================================================================
 *  LESSON 13 — PRODUCTION HARDENING: EVERYTHING ELSE THAT MATTERS
 * =====================================================================
 *
 * Run:  mvn spring-boot:run -Dspring-boot.run.profiles=lesson13
 *
 * Lessons 1-12 built the authentication/authorization core. Production
 * security is that PLUS a checklist of less glamorous controls. This
 * config is a realistic hardened baseline; the javadoc is the checklist.
 *
 * ---------------------------------------------------------------------
 *  1. SECURITY HEADERS (configured below — curl -i and LOOK at them)
 * ---------------------------------------------------------------------
 *  Strict-Transport-Security (HSTS)
 *      Browser: "talk HTTPS to this host for the next year, refuse
 *      downgrade". Kills SSL-stripping MITM. Meaningful only over HTTPS.
 *  Content-Security-Policy (CSP)
 *      Whitelist of where scripts/styles/images may load from. THE
 *      anti-XSS header: injected <script src=//evil> simply won't run.
 *      Start restrictive, loosen per real need — never the reverse.
 *  X-Content-Type-Options: nosniff
 *      Stops browsers "guessing" content types (uploaded .jpg that is
 *      really HTML+JS would otherwise execute).
 *  X-Frame-Options / frame-ancestors: DENY
 *      Your site can't be put in an <iframe> -> clickjacking dead
 *      (invisible frame over a decoy button, victim clicks "transfer").
 *  Referrer-Policy: strict-origin-when-cross-origin
 *      Outbound links don't leak your full URLs (which may contain ids).
 *  Cache-Control: no-store (Spring sets by default on secured responses)
 *      Authenticated pages must not land in shared caches.
 *
 * ---------------------------------------------------------------------
 *  2. TLS EVERYWHERE
 * ---------------------------------------------------------------------
 *  Everything in this course rides on it: basic auth is base64 (= plain
 *  text), session cookies and bearer tokens are identity — over HTTP
 *  they're all free to any network sniffer. In real deployments TLS
 *  usually TERMINATES AT THE EDGE (load balancer / ingress); the app
 *  then trusts X-Forwarded-* headers — set
 *      server.forwarded-headers-strategy: framework
 *  so Spring knows the original request was HTTPS (affects Secure
 *  cookies, redirect URLs, HSTS).
 *
 * ---------------------------------------------------------------------
 *  3. SECRETS MANAGEMENT
 * ---------------------------------------------------------------------
 *  NOTHING sensitive in git: no passwords, no API keys, no JWT secrets.
 *  Ladder, worst to best:
 *      hardcoded < gitignored file < env vars < secret manager
 *      (Vault / AWS Secrets Manager / K8s secrets) with rotation.
 *  yml files reference, never contain:   password: ${DB_PASSWORD}
 *  Rotate anything that ever leaked — assume copied the moment it
 *  touched a commit, a log line, or a Slack message.
 *
 * ---------------------------------------------------------------------
 *  4. AUDIT LOGGING (see SecurityAuditLogger in this package — it runs!)
 * ---------------------------------------------------------------------
 *  Log every auth success/failure with who/when/where. Feeds intrusion
 *  detection ("50 failures then a success on admin"), forensics, and
 *  compliance (SOC2/PCI demand it). Spring publishes
 *  AuthenticationSuccessEvent / AbstractAuthenticationFailureEvent for
 *  free — listen and ship to your log pipeline. NEVER log the password.
 *
 * ---------------------------------------------------------------------
 *  5. BRUTE-FORCE & ABUSE PROTECTION
 * ---------------------------------------------------------------------
 *  Rate-limit login endpoints (per user AND per IP), lock accounts
 *  after N failures (with unlock flow), add CAPTCHA past a threshold.
 *  You built the real thing already — the RateLimiter service sitting
 *  next to this project is exactly this control at the gateway layer.
 *
 * ---------------------------------------------------------------------
 *  6. DEPENDENCIES & DISCLOSURE
 * ---------------------------------------------------------------------
 *  Most breaches exploit KNOWN CVEs in old libraries. Automate:
 *  Dependabot/Renovate + `mvn versions:display-dependency-updates` in
 *  CI. Also: error responses must not leak stack traces or versions
 *  (server.error.include-stacktrace: never), and actuator endpoints
 *  must be locked down (management.endpoints exposure — health only).
 *
 * ---------------------------------------------------------------------
 *  7. THE CHECKLIST (print this)
 * ---------------------------------------------------------------------
 *   [ ] TLS everywhere, HSTS on, forwarded headers configured
 *   [ ] BCrypt/Argon2 passwords (L4), encode at registration (L5)
 *   [ ] deny-by-default authorization (L3) + method security (L6)
 *   [ ] CSRF on for cookie apps (L7), exact-origin CORS (L7)
 *   [ ] session: HttpOnly+Secure+SameSite, max sessions (L9) — or
 *       stateless JWT, short expiry + refresh (L10/L12)
 *   [ ] secrets in a manager, not in git (3)
 *   [ ] security headers: CSP, nosniff, frame DENY (1)
 *   [ ] audit log auth events (4), alert on anomalies
 *   [ ] rate limiting + account lockout on login (5)
 *   [ ] dependency scanning in CI, stack traces hidden (6)
 *   [ ] pen test / OWASP ZAP scan before go-live
 *
 * =====================================================================
 *  TRY IT
 * =====================================================================
 *  A. curl -i -u ops:ops-pass http://localhost:8080/private/hello
 *     -> read EVERY response header, map each to section 1.
 *  B. Fail a login on purpose (wrong password), watch the console:
 *     SecurityAuditLogger prints the failure event with username + reason.
 *     Then succeed and see the success event.
 *  C. curl -i http://localhost:8080/actuator/env  -> 403 even
 *     authenticated as ops (denyAll on actuator internals; only
 *     /actuator/health is open — and only for LB probes).
 *
 * =====================================================================
 *  KEY TAKEAWAY
 * =====================================================================
 *  Authentication is a feature; security is a POSTURE: headers, TLS,
 *  secrets, audit, rate limits, patched dependencies — maintained
 *  forever, reviewed every release. The checklist is the deliverable.
 * =====================================================================
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("lesson13")
public class Lesson13_ProductionConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()   // LB probe only
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/private/**").hasRole("USER")
                .anyRequest().denyAll()                            // deny-by-default, always
            )
            .headers(headers -> headers
                // HSTS: one year, cover subdomains (browser honors over HTTPS)
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                // CSP: only own-origin scripts/styles; no framing by anyone
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self'; style-src 'self'; " +
                    "img-src 'self' data:; frame-ancestors 'none'"))
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(rp -> rp.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            .sessionManagement(session -> session
                .sessionFixation(f -> f.changeSessionId())
                .maximumSessions(1)
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            .httpBasic(Customizer.withDefaults())
            .formLogin(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Authorization DENIED events are not published unless this bean
     * exists (auth success/failure events are free; denial events are
     * opt-in because they can be high-volume). Required for
     * SecurityAuditLogger.onDenied to fire.
     */
    @Bean
    org.springframework.security.authorization.AuthorizationEventPublisher authorizationEventPublisher(
            org.springframework.context.ApplicationEventPublisher publisher) {
        return new org.springframework.security.authorization.SpringAuthorizationEventPublisher(publisher);
    }

    /**
     * Demo users, encoded properly. In the real deployment this bean is
     * replaced by the L5 JPA service (or L11/L12 OIDC — no local users at
     * all) and credentials come from the secret manager, not source code.
     */
    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails ops = User.builder()
                .username("ops").password(encoder.encode("ops-pass")).roles("USER").build();
        UserDetails admin = User.builder()
                .username("admin").password(encoder.encode("admin-pass")).roles("USER", "ADMIN").build();
        return new InMemoryUserDetailsManager(ops, admin);
    }
}
