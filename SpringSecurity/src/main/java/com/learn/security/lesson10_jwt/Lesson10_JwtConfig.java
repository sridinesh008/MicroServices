package com.learn.security.lesson10_jwt;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * =====================================================================
 *  LESSON 10 — JWT: STATELESS AUTH FOR APIs (how microservices do it)
 * =====================================================================
 *
 * Run:  mvn spring-boot:run -Dspring-boot.run.profiles=lesson10
 *
 * THE PROBLEM WITH SESSIONS AT SCALE
 * ----------------------------------
 * Session = server-side memory. Three instances behind a load balancer
 * -> the session lives on instance A, request lands on instance B ->
 * you're logged out. Fixes (sticky sessions, shared Redis) add moving
 * parts. The alternative: put the identity IN THE TOKEN and make it
 * tamper-proof. Any instance can verify it with no lookup. That is JWT.
 *
 * ANATOMY: xxxxx.yyyyy.zzzzz  (three base64url parts)
 *   header    {"alg":"HS256"}                       how it's signed
 *   payload   {"sub":"alice","roles":["USER"],      the CLAIMS
 *              "iat":1720000000,"exp":1720003600}
 *   signature HMAC-SHA256(header + "." + payload, secret)
 *
 * READ CAREFULLY — the payload is only ENCODED, not ENCRYPTED. Anyone
 * can read it (paste any JWT into jwt.io). Never put secrets in claims.
 * What the signature gives you is INTEGRITY: change one character of
 * the payload and the signature no longer matches. Only holders of the
 * secret can produce a valid signature -> claims can be TRUSTED, not
 * hidden.
 *
 * THE FLOW
 * --------
 *   1. POST /token with basic auth  -> server issues a signed JWT (1h)
 *   2. every API call:  Authorization: Bearer <jwt>
 *   3. BearerTokenAuthenticationFilter validates SIGNATURE + EXPIRY,
 *      reads claims, builds the Authentication. NO STORAGE TOUCHED.
 *
 * TWO FILTER CHAINS — new concept!
 * --------------------------------
 * The /token endpoint must accept basic auth; everything else must
 * accept ONLY bearer tokens. One chain can't cleanly do both, so we
 * define TWO SecurityFilterChain beans:
 *   @Order(1) chain with .securityMatcher("/token")  — claims /token only
 *   @Order(2) chain                                  — everything else
 * FilterChainProxy (Lesson 1!) picks the FIRST chain whose matcher fits
 * the request. This is also how real systems mix "web pages with form
 * login" + "API with JWT" in one app.
 *
 * TRADE-OFFS (interview material)
 * -------------------------------
 *   + stateless: any instance validates, nothing shared
 *   + cross-service: service B trusts a token minted by service A
 *   - NO REVOCATION: valid until exp, full stop. Stolen token works
 *     until expiry. Mitigations: short exp (minutes) + refresh tokens,
 *     or a denylist (which quietly reintroduces state).
 *   - HS256 (one shared secret) fine within one team; ecosystems use
 *     RS256 key PAIRS: private key signs at the auth server, public key
 *     verifies anywhere — verifiers can't mint tokens. Lessons 11-12
 *     territory (Lesson 12 does it hands-on).
 *
 * =====================================================================
 *  TRY IT
 * =====================================================================
 *  A. Get a token (PowerShell-friendly):
 *       curl -s -u alice:alice-pass -X POST http://localhost:8080/token
 *     Response = one long string. Copy it. Decode the middle part on
 *     jwt.io — read your own claims.
 *  B. curl -i http://localhost:8080/private/hello
 *     -> 401 with header  WWW-Authenticate: Bearer  (chain 2 speaks JWT only)
 *  C. curl -i -H "Authorization: Bearer <token>" http://localhost:8080/private/hello
 *     -> 200 as alice. No session cookie anywhere in the response.
 *  D. Tamper: change ONE character in the token's payload part, resend
 *     -> 401. Signature check failed. This is the whole trick.
 *  E. bob's token (roles ADMIN) on /admin/hello -> 200; alice's -> 403.
 *     Roles now travel INSIDE the token — look at the converter bean to
 *     see how the "roles" claim becomes ROLE_* authorities.
 *
 * =====================================================================
 *  KEY TAKEAWAYS
 * =====================================================================
 *  - JWT = signed (readable!) claims; integrity, not secrecy.
 *  - Stateless: sessions/CSRF gone; revocation becomes your problem.
 *  - Multiple SecurityFilterChain beans + securityMatcher + @Order.
 *  - Short expiry always; HS256 shared-secret only inside one trust zone.
 * =====================================================================
 */
@Configuration
@EnableWebSecurity
@Profile("lesson10")
public class Lesson10_JwtConfig {

    /** Chain 1: ONLY /token. Basic auth in, JWT out. */
    @Bean
    @Order(1)
    SecurityFilterChain tokenEndpointChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/token")                       // <- this chain claims only /token
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable());                   // token clients, no cookies
        return http.build();
    }

    /** Chain 2: everything else. Bearer JWT only — no basic, no forms, no sessions. */
    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/private/**").hasRole("USER")
                .anyRequest().denyAll()
            )
            .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable());                   // stateless -> no CSRF surface
        return http.build();
    }

    /**
     * HS256 secret. From configuration, min 32 bytes (HMAC-SHA256 needs
     * 256 bits). Demo default lives in application.yml — in production
     * this comes from a secret manager, never from a file in git (L13).
     */
    private SecretKey secretKey(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** Signs tokens (used by TokenController). */
    @Bean
    JwtEncoder jwtEncoder(@Value("${lesson10.jwt.secret}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(secret)));
    }

    /** Verifies signature + expiry on every request (BearerTokenAuthenticationFilter). */
    @Bean
    JwtDecoder jwtDecoder(@Value("${lesson10.jwt.secret}") String secret) {
        return NimbusJwtDecoder.withSecretKey(secretKey(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Claims -> authorities mapping. Default expects a "scope" claim
     * (OAuth2 style); our tokens carry "roles": ["USER"] instead, so:
     * read claim "roles", prefix each with "ROLE_" -> hasRole works.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** Users who may EXCHANGE credentials for a token (chain 1). */
    @Bean
    UserDetailsService userDetailsService() {
        UserDetails alice = User.builder()
                .username("alice").password("{noop}alice-pass").roles("USER").build();
        UserDetails bob = User.builder()
                .username("bob").password("{noop}bob-pass").roles("USER", "ADMIN").build();
        return new InMemoryUserDetailsManager(alice, bob);
    }
}
