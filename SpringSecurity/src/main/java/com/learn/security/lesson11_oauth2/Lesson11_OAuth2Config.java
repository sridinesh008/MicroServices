package com.learn.security.lesson11_oauth2;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * =====================================================================
 *  LESSON 11 — OAUTH2 & OPENID CONNECT: DELEGATING IDENTITY
 * =====================================================================
 *
 * Run:  mvn spring-boot:run -Dspring-boot.run.profiles=lesson11
 * (Boots with a placeholder Google registration — the redirect flow
 *  starts but Google will reject the dummy client id. Follow SETUP
 *  below with real credentials to complete a login.)
 *
 * THE IDEA
 * --------
 * Lessons 2-10: WE stored passwords, WE verified them. OAuth2/OIDC says:
 * don't. Let a dedicated identity provider (Google, GitHub, Azure AD,
 * Keycloak, Okta) do logins; your app receives a signed proof of who the
 * user is. No password DB to breach, MFA/rate-limiting/breach-detection
 * are Google's problem, and users skip yet another password.
 *
 * NAMES FIRST (exam material)
 * ---------------------------
 *   OAuth2  = AUTHORIZATION framework: "app X may access resource Y on
 *             the user's behalf" — about PERMISSION, not identity
 *   OIDC    = OpenID Connect, thin IDENTITY layer on top of OAuth2:
 *             adds the ID TOKEN — a JWT (Lesson 10!) with sub, email,
 *             name — signed with the provider's RS256 PRIVATE key,
 *             verified by anyone with the PUBLIC key (fetched from the
 *             provider's /.well-known/jwks endpoint automatically)
 *   Roles in the play:
 *     Resource Owner        the human
 *     Client                YOUR app (this one)
 *     Authorization Server  Google / Keycloak / ...
 *     Resource Server       an API accepting the resulting tokens (L10!)
 *
 * THE AUTHORIZATION CODE FLOW (the one that matters; memorize the shape)
 * ----------------------------------------------------------------------
 *   1. user clicks "Login with Google" -> redirect to Google with
 *      client_id, redirect_uri, scope, and random `state` (anti-CSRF)
 *   2. user authenticates AT GOOGLE. Password never touches your app.
 *   3. Google redirects back: /login/oauth2/code/google?code=abc&state=...
 *   4. YOUR SERVER (not the browser) exchanges code + client_secret for
 *      tokens — back channel, invisible to browser/XSS
 *   5. ID token verified (signature, issuer, audience, expiry) ->
 *      user is logged in; a session starts (Lesson 9 machinery)
 *   Why the code detour instead of tokens in the redirect? The browser
 *   URL leaks (history, logs, Referer). The code is one-time-use and
 *   worthless without the client_secret, which lives only server-side.
 *
 * WHAT SPRING DOES FOR YOU: .oauth2Login() below installs filters that
 * handle steps 1, 3, 4, 5 — the redirect dance, state checking, code
 * exchange, JWKS fetching, token validation. You write ZERO of it. The
 * yml block (lesson11 section of application.yml) supplies client-id,
 * client-secret, and for non-Google providers the issuer-uri.
 *
 * THE OTHER HALF: your APIs as a RESOURCE SERVER accepting Google/
 * Keycloak-issued ACCESS tokens — exactly Lesson 10's config with one
 * change: instead of a local secret, point the decoder at the provider:
 *
 *     spring.security.oauth2.resourceserver.jwt.issuer-uri: https://...
 *
 * Spring fetches the public keys and validates RS256 signatures. This
 * pair (oauth2Login for humans, resource server for APIs) is THE
 * standard microservice identity architecture: one Keycloak/Okta in the
 * middle, every service validating the same tokens, zero password DBs.
 *
 * =====================================================================
 *  SETUP (to complete a real login)
 * =====================================================================
 *  1. https://console.cloud.google.com -> APIs & Services -> Credentials
 *     -> Create OAuth client ID (Web application)
 *  2. Authorized redirect URI:
 *     http://localhost:8080/login/oauth2/code/google
 *  3. Put the issued id/secret into ENVIRONMENT VARIABLES (see yml):
 *     set GOOGLE_CLIENT_ID=...        (never commit them — Lesson 13)
 *     set GOOGLE_CLIENT_SECRET=...
 *  4. Restart, browser -> http://localhost:8080/private/hello
 *     -> Google login -> back with 200. /user shows your OIDC claims.
 *
 * =====================================================================
 *  KEY TAKEAWAYS
 * =====================================================================
 *  - OAuth2 = delegated authorization; OIDC adds identity (ID token=JWT).
 *  - Authorization Code flow: code in the front channel, secret+tokens
 *    in the back channel. `state` blocks CSRF on the redirect.
 *  - oauth2Login() = "be a client"; oauth2ResourceServer() = "accept
 *    tokens". Microservices: one IdP, both halves, no password DBs.
 *  - RS256 asymmetric beats HS256 shared-secret across trust boundaries.
 * =====================================================================
 */
@Configuration
@EnableWebSecurity
@Profile("lesson11")
public class Lesson11_OAuth2Config {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
            )
            // the entire redirect dance, code exchange, and token
            // validation hide behind this one call:
            .oauth2Login(Customizer.withDefaults());
        return http.build();
    }
    // NOTE: no UserDetailsService, no PasswordEncoder — nothing to store,
    // nothing to hash. That absence is the lesson.
}
