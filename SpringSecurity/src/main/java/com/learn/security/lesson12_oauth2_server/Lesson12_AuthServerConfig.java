package com.learn.security.lesson12_oauth2_server;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

/**
 * =====================================================================
 *  LESSON 12 — RUN YOUR OWN OAUTH2 AUTHORIZATION SERVER (all local!)
 * =====================================================================
 *
 * Run:  mvn spring-boot:run -Dspring-boot.run.profiles=lesson12
 *
 * Lesson 11 delegated identity to Google — but you couldn't finish the
 * flow without real credentials, and Google hides the server half. This
 * lesson removes the curtain: THIS app now plays ALL THREE roles:
 *
 *   AUTHORIZATION SERVER  Spring Authorization Server, issues tokens
 *   RESOURCE SERVER       /api/** validates those tokens (Lesson 10 tech)
 *   (you + curl play the client)
 *
 * Everything Google did in Lesson 11 happens HERE, inspectable with curl.
 *
 * ---------------------------------------------------------------------
 *  NEW CONCEPT 1: CLIENT CREDENTIALS FLOW (machine-to-machine)
 * ---------------------------------------------------------------------
 * No human anywhere: billing-service needs to call user-service. The
 * CLIENT ITSELF authenticates (client_id + client_secret) and receives
 * an access token:
 *
 *   POST /oauth2/token  grant_type=client_credentials&scope=api.read
 *
 * Compare Lesson 8's API key: same job, but standardized, expiring,
 * scoped, and verifiable by ANY service holding the public key — no
 * shared secret spread across the fleet. This flow is the backbone of
 * microservice fleets and the reason to prefer OAuth2 over ad-hoc keys.
 *
 * ---------------------------------------------------------------------
 *  NEW CONCEPT 2: SCOPES vs ROLES
 * ---------------------------------------------------------------------
 * Roles (L3) describe a USER's job: what alice may do.
 * Scopes describe a TOKEN's reach: what THIS CLIENT was granted —
 * possibly a narrow slice of what the user could do ("read-only access
 * to your calendar"). Resource server maps the token's scope claim to
 * authorities "SCOPE_api.read" automatically; rules use
 * hasAuthority("SCOPE_api.read"). Note the contrast with Lesson 10
 * where we mapped a custom "roles" claim by hand.
 *
 * ---------------------------------------------------------------------
 *  NEW CONCEPT 3: RS256 — ASYMMETRIC SIGNING DONE FOR REAL
 * ---------------------------------------------------------------------
 * Lesson 10 used HS256: ONE shared secret both signs and verifies —
 * whoever can verify can also MINT. Unacceptable across services.
 * Here the server generates an RSA KEY PAIR at startup (jwkSource bean):
 *   private key -> signs tokens, never leaves this server
 *   public key  -> published at /oauth2/jwks, anyone may verify
 * Resource servers fetch the JWKS and verify signatures without being
 * able to forge tokens. Rotate keys by publishing a new key alongside
 * the old (kid header on each JWT picks the right one).
 *
 * ---------------------------------------------------------------------
 *  NEW CONCEPT 4: DISCOVERY
 * ---------------------------------------------------------------------
 * GET /.well-known/openid-configuration returns a JSON map of every
 * endpoint (token, authorize, jwks, userinfo...). This is how Lesson
 * 11's  issuer-uri: <one URL>  was enough configuration: Spring fetched
 * this document and wired everything from it.
 *
 * ---------------------------------------------------------------------
 *  THE FILTER-CHAIN LAYOUT (Lesson 10's multi-chain idea, grown up)
 * ---------------------------------------------------------------------
 *   @Order(1)  authorization-server endpoints (/oauth2/*, /.well-known/*)
 *              — installed by OAuth2AuthorizationServerConfigurer
 *   @Order(2)  the app: /api/** as resource server (bearer JWT),
 *              formLogin for the human step of the auth-code flow
 *
 * =====================================================================
 *  TRY IT
 * =====================================================================
 *  A. Discovery — read the server's self-description:
 *     curl -s http://localhost:8080/.well-known/openid-configuration
 *  B. Public keys (what resource servers fetch):
 *     curl -s http://localhost:8080/oauth2/jwks
 *  C. CLIENT CREDENTIALS — machine gets a token:
 *     curl -s -u m2m-client:m2m-secret -X POST http://localhost:8080/oauth2/token \
 *          -d "grant_type=client_credentials&scope=api.read"
 *     -> {"access_token":"eyJ...","scope":"api.read","expires_in":299,...}
 *     Decode it on jwt.io: header has "alg":"RS256" and a "kid" matching
 *     a key from B. Claims: iss, sub=m2m-client, scope.
 *  D. Spend it:
 *     curl -i -H "Authorization: Bearer <access_token>" http://localhost:8080/api/data
 *     -> 200
 *  E. Scope enforcement — same token, endpoint needing api.write:
 *     curl -i -H "Authorization: Bearer <access_token>" -X POST http://localhost:8080/api/write
 *     -> 403. Token valid, scope insufficient. Roles never entered the
 *        picture — pure scope decision.
 *  F. AUTHORIZATION CODE flow with a human (browser):
 *     1. visit:
 *        http://localhost:8080/oauth2/authorize?response_type=code&client_id=web-client&scope=openid%20api.read&redirect_uri=http://127.0.0.1:8080/public/callback
 *     2. log in alice/alice-pass -> CONSENT SCREEN (the "this app wants
 *        to..." page — you built Google's consent page just now)
 *     3. approve -> redirected to /public/callback?code=...
 *     4. exchange the code (what Lesson 11 step 4 did behind your back):
 *        curl -s -u web-client:web-secret -X POST http://localhost:8080/oauth2/token \
 *             -d "grant_type=authorization_code&code=<code>&redirect_uri=http://127.0.0.1:8080/public/callback"
 *        -> access_token + id_token (OIDC!) + refresh_token
 *  G. REFRESH — trade the refresh_token for a fresh access token:
 *     curl -s -u web-client:web-secret -X POST http://localhost:8080/oauth2/token \
 *          -d "grant_type=refresh_token&refresh_token=<refresh_token>"
 *     This pair (short access + long refresh) is the answer to Lesson
 *     10's "no revocation" problem: access tokens die in minutes;
 *     refresh tokens are server-side state that CAN be revoked.
 *
 * =====================================================================
 *  KEY TAKEAWAYS
 * =====================================================================
 *  - client_credentials = machine-to-machine; authorization_code =
 *    humans; refresh_token = short-lived access without re-login.
 *  - Scopes bound the TOKEN, roles bound the USER. SCOPE_ authorities.
 *  - RS256 + JWKS: verify-many, mint-one. HS256 only inside one service.
 *  - Discovery document turns an issuer URL into full configuration.
 *  - In production this whole class is usually a separate deployment
 *    (Keycloak/Okta/this) — embedded here so you can SEE all of it.
 *  - NOT shown here but MUST-KNOW: PKCE. Public clients (SPAs, mobile)
 *    can't keep a client_secret; they send a hashed one-time
 *    code_challenge instead and prove ownership at exchange time.
 *    OAuth 2.1 mandates PKCE for all auth-code clients. See INTERVIEW.md.
 * =====================================================================
 */
@Configuration
@EnableWebSecurity
@Profile("lesson12")
public class Lesson12_AuthServerConfig {

    /**
     * Chain 1: the authorization server's own endpoints. The configurer
     * brings its matcher (only /oauth2/*, /.well-known/* etc. hit this
     * chain) and all the protocol filters. oidc() enables ID tokens,
     * userinfo, and the discovery document.
     */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults());
        http
            // browser hitting /oauth2/authorize unauthenticated -> login page
            .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
            // userinfo endpoint accepts the access tokens we mint
            .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * Chain 2: the application. /api/** = resource server validating our
     * own RS256 tokens; formLogin serves the human step of flow F.
     */
    @Bean
    @Order(2)
    SecurityFilterChain appChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/error").permitAll()
                // scope-based rules — note SCOPE_ prefix, not ROLE_
                .requestMatchers("/api/write").hasAuthority("SCOPE_api.write")
                .requestMatchers("/api/**").hasAuthority("SCOPE_api.read")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))   // bearer clients, no cookies
            .formLogin(Customizer.withDefaults());
        return http.build();
    }

    /**
     * The client REGISTRY — Google Cloud Console's "Credentials" page,
     * in code. Two clients, two flows.
     */
    @Bean
    RegisteredClientRepository registeredClientRepository() {
        // machine client: no human, no redirect, just credentials + scopes
        RegisteredClient m2m = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("m2m-client")
                .clientSecret("{noop}m2m-secret")   // yes, encode in prod (Lesson 4!)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("api.read")
                .scope("api.write")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(5))   // short! L10 lesson applied
                        .build())
                .build();

        // human-facing client: full authorization-code + refresh
        RegisteredClient web = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("web-client")
                .clientSecret("{noop}web-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                // loopback IP, not "localhost" — the spec (RFC 8252) requires it
                .redirectUri("http://127.0.0.1:8080/public/callback")
                .scope(OidcScopes.OPENID)
                .scope("api.read")
                // show the consent screen (Google always does; see flow F step 2)
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                .build();

        return new InMemoryRegisteredClientRepository(m2m, web);
    }

    /**
     * RSA key pair, generated per startup (prod: loaded from a keystore /
     * KMS and ROTATED, old keys kept until their tokens expire).
     * Private half signs; public half is served at /oauth2/jwks.
     */
    @Bean
    JWKSource<SecurityContext> jwkSource() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())   // the "kid" in every JWT header
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    /** Resource-server half decodes with the SAME key source — public part only. */
    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /** issuer-uri etc.; defaults derive everything from the request URL. */
    @Bean
    AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

    /** The human who logs in during flow F. */
    @Bean
    UserDetailsService userDetailsService() {
        UserDetails alice = User.builder()
                .username("alice").password("{noop}alice-pass").roles("USER").build();
        return new InMemoryUserDetailsManager(alice);
    }
}
