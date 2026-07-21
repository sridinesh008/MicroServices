package com.learn.security.lesson08_custom_filters;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * =====================================================================
 *  LESSON 8 (part 2/2) — PLUGGING YOUR FILTER INTO THE CHAIN
 * =====================================================================
 *
 * Run:  mvn spring-boot:run -Dspring-boot.run.profiles=lesson08
 *
 * Read ApiKeyFilter FIRST — it is the meat. This class does the wiring.
 *
 * WHERE IN THE CHAIN? Order matters (remember the Lesson 1 printout).
 * Authentication filters must run:
 *   - AFTER  SecurityContextHolderFilter (context storage must exist)
 *   - BEFORE AuthorizationFilter          (the gate needs the identity)
 * Convention: hook in at the position of UsernamePasswordAuthenticationFilter,
 * where all the "establish identity" filters cluster:
 *
 *   .addFilterBefore(myFilter, UsernamePasswordAuthenticationFilter.class)
 *
 * (also available: addFilterAfter, addFilterAt)
 *
 * TWO WAYS CLIENTS CAN AUTHENTICATE IN THIS LESSON — note how the
 * filters coexist without knowing about each other:
 *   humans:    -u alice:alice-pass      (BasicAuthenticationFilter)
 *   machines:  -H "X-API-KEY: ..."      (our ApiKeyFilter)
 *
 * GOTCHA WORTH REMEMBERING: do NOT annotate a security filter with
 * @Component in a Boot app. Boot auto-registers every Filter bean with
 * the SERVLET container, so it would run for every request OUTSIDE the
 * security chain too — double execution, subtle bugs. Instantiate it
 * here and register it on HttpSecurity only.
 *
 * STATELESS: API-key clients shouldn't get JSESSIONID cookies — every
 * request carries the key anyway. SessionCreationPolicy.STATELESS turns
 * session creation off (full session story: Lesson 9).
 *
 * =====================================================================
 *  TRY IT
 * =====================================================================
 *  A. curl -i http://localhost:8080/api/data
 *     -> 401. No key, no basic auth — nobody authenticated the request.
 *  B. curl -i -H "X-API-KEY: super-secret-key-123" http://localhost:8080/api/data
 *     -> 200 as "api-client". Your filter authenticated it.
 *  C. curl -i -H "X-API-KEY: wrong" http://localhost:8080/api/data
 *     -> 401 immediately (contract rule 4 — invalid key never falls through).
 *  D. curl -i -u alice:alice-pass http://localhost:8080/private/hello
 *     -> 200. Basic auth still works — the filters coexist.
 *  E. curl -i -H "X-API-KEY: super-secret-key-123" http://localhost:8080/private/hello
 *     -> 403! api-client has ROLE_API, /private/** wants ROLE_USER.
 *        Your custom-authenticated identity flows through the SAME
 *        authorization machinery as everything else. That's the payoff
 *        of using SecurityContextHolder correctly.
 *
 * =====================================================================
 *  KEY TAKEAWAYS
 * =====================================================================
 *  - OncePerRequestFilter + the 4-step contract = any custom auth scheme.
 *  - SecurityContextHolder is the handshake with the rest of Spring
 *    Security; set an authenticated Authentication and you're a citizen.
 *  - addFilterBefore(..., UsernamePasswordAuthenticationFilter.class).
 *  - Never @Component a security filter in Boot (double registration).
 * =====================================================================
 */
@Configuration
@EnableWebSecurity
@Profile("lesson08")
public class Lesson08_CustomFilterConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${lesson08.api-key:super-secret-key-123}") String apiKey) throws Exception {

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/error").permitAll()          // sendError() re-dispatches here (see L7 javadoc)
                .requestMatchers("/api/**").hasRole("API")      // machines only
                .requestMatchers("/private/**").hasRole("USER") // humans only
                .anyRequest().denyAll()
            )
            .addFilterBefore(new ApiKeyFilter(apiKey), UsernamePasswordAuthenticationFilter.class)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())   // no sessions -> no CSRF surface (Lesson 7 rule)
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        UserDetails alice = User.builder()
                .username("alice").password("{noop}alice-pass").roles("USER").build();
        return new InMemoryUserDetailsManager(alice);
    }
}
