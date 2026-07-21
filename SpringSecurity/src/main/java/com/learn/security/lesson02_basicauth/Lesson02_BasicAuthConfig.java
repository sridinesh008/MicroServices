package com.learn.security.lesson02_basicauth;

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

/**
 * =====================================================================
 *  LESSON 2 — YOUR FIRST SecurityFilterChain + REAL (IN-MEMORY) USERS
 * =====================================================================
 *
 * Run:  mvn spring-boot:run -Dspring-boot.run.profiles=lesson02
 *
 * In Lesson 1, Boot auto-configured everything. The moment YOU define a
 * SecurityFilterChain bean (or a UserDetailsService bean), Boot's
 * auto-configuration BACKS OFF and your beans take over. Watch the
 * startup log: the "Using generated security password" line is GONE,
 * because our UserDetailsService below replaced the generated user.
 *
 * TWO BEANS = THE WHOLE STORY OF THIS LESSON
 * ------------------------------------------
 *
 * 1. SecurityFilterChain  — WHAT is protected and HOW you may log in.
 *    Built with the HttpSecurity DSL (a builder). Each method call
 *    configures one of the filters you saw printed in Lesson 1.
 *
 * 2. UserDetailsService   — WHO the users are.
 *    Core interface with ONE method:
 *
 *        UserDetails loadUserByUsername(String username)
 *
 *    Spring Security calls it during login: "give me the stored user
 *    for this username, I'll check the password myself." Today the
 *    implementation is a Map in memory (InMemoryUserDetailsManager);
 *    in Lesson 5 the SAME interface will be backed by a database.
 *    Swapping storage never changes the interface — that is the design.
 *
 * WHAT IS UserDetails?
 * --------------------
 * Spring Security's contract for "a user record": username, password,
 * authorities (roles), plus account flags (locked, expired, enabled).
 * User.builder() is a convenient builder for it.
 *
 * ABOUT {noop} — READ THIS, THEN FORGET YOU EVER SAW IT
 * -----------------------------------------------------
 * Password strings carry a prefix telling Spring which encoder to use:
 *   "{noop}password"  -> no encoding, plain text comparison
 *   "{bcrypt}$2a$..." -> BCrypt hash
 * {noop} exists ONLY for demos like this one. Plain-text passwords in a
 * real system are a fireable offense. Lesson 4 is entirely about doing
 * this properly — the prefix mechanism (DelegatingPasswordEncoder) will
 * make sense there.
 *
 * READING THE DSL BELOW
 * ---------------------
 *   authorizeHttpRequests -> configures AuthorizationFilter (the gate,
 *                            last filter in the chain)
 *     .requestMatchers("/public/**").permitAll()
 *                         -> URL pattern rule. permitAll = no login needed.
 *                            ** matches any depth of subpaths.
 *     .anyRequest().authenticated()
 *                         -> CATCH-ALL for everything not matched above.
 *                            RULES ARE CHECKED TOP-DOWN, FIRST MATCH WINS.
 *                            Catch-all must be LAST — the compiler even
 *                            enforces you can't add rules after anyRequest().
 *                            Always end with a catch-all: whatever endpoint
 *                            a teammate adds next month is secure by default.
 *   httpBasic             -> enables BasicAuthenticationFilter
 *                            (Authorization: Basic <base64 user:pass> header)
 *   formLogin             -> enables the /login page + UsernamePasswordAuthenticationFilter
 *
 *   Customizer.withDefaults() just means "enable it with default settings".
 *
 * =====================================================================
 *  TRY IT
 * =====================================================================
 *
 *  A. curl -i http://localhost:8080/public/hello
 *     -> 200 WITHOUT login. First time in this course! Compare Lesson 1.
 *
 *  B. curl -i http://localhost:8080/private/hello
 *     -> 401. Catch-all rule caught it.
 *
 *  C. curl -i -u alice:alice-pass http://localhost:8080/private/hello
 *     -> 200, greets "alice". Basic auth against OUR user, not generated one.
 *
 *  D. curl -i -u alice:wrong http://localhost:8080/private/hello
 *     -> 401. Bad password.
 *
 *  E. THE CLIFFHANGER:
 *     curl -i -u alice:alice-pass http://localhost:8080/admin/hello
 *     -> 200 (!!). alice has role USER, not ADMIN — but our rules only
 *        say "authenticated". Roles exist on the user now, yet NOTHING
 *        checks them. Lesson 3 closes this hole.
 *
 *  F. Browser: http://localhost:8080/private/hello
 *     -> still get the login page (formLogin). Log in as bob / bob-pass.
 *
 * =====================================================================
 *  KEY TAKEAWAYS
 * =====================================================================
 *  - Define your own SecurityFilterChain/UserDetailsService -> Boot's
 *    defaults back off. You own security now.
 *  - authorizeHttpRequests rules: top-down, first match wins, always end
 *    with anyRequest() catch-all.
 *  - UserDetailsService = "load user by username". Same interface from
 *    in-memory demo to production database.
 *  - Having a role and enforcing a role are different things.
 * =====================================================================
 */
@Configuration
@EnableWebSecurity
@Profile("lesson02")
public class Lesson02_BasicAuthConfig {

    /**
     * WHAT is protected, HOW you may log in.
     * This single bean replaces the entire Lesson 1 auto-configuration.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .formLogin(Customizer.withDefaults());
        return http.build();
    }

    /**
     * WHO the users are. Two users with different roles — the roles do
     * nothing yet (see cliffhanger E above), but they are stored and ready
     * for Lesson 3.
     */
    @Bean
    UserDetailsService userDetailsService() {
        UserDetails alice = User.builder()
                .username("alice")
                .password("{noop}alice-pass")   // {noop} = demo only! Lesson 4 fixes this.
                .roles("USER")                  // stored as authority "ROLE_USER" internally
                .build();

        UserDetails bob = User.builder()
                .username("bob")
                .password("{noop}bob-pass")
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(alice, bob);
    }
}
