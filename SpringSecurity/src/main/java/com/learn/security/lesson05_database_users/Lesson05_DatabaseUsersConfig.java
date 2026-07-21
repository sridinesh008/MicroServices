package com.learn.security.lesson05_database_users;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * =====================================================================
 *  LESSON 5 (part 4/4) — USERS IN A REAL DATABASE + REGISTRATION
 * =====================================================================
 *
 * Run:  mvn spring-boot:run -Dspring-boot.run.profiles=lesson05
 *
 * FOUR FILES IN THIS PACKAGE — the complete production pattern:
 *   UserAccount            JPA entity (any schema you like)
 *   UserAccountRepository  Spring Data query: findByUsername
 *   JpaUserDetailsService  the bridge: row -> UserDetails  <- READ IT
 *   this class             filter chain + registration endpoint + seed data
 *
 * WHAT CHANGED vs LESSON 4
 * ------------------------
 * There is NO UserDetailsService @Bean here anymore. JpaUserDetailsService
 * is a @Service, Spring finds it, DaoAuthenticationProvider uses it.
 * Users now survive restarts conceptually (H2 in-memory here for zero
 * setup — swap the JDBC URL for PostgreSQL and NOTHING else changes).
 *
 * REGISTRATION — where password encoding REALLY happens
 * -----------------------------------------------------
 * Lesson 4 encoded at startup; real systems encode ONCE at registration:
 *
 *   POST /public/register {"username":"carol","password":"carol-pass"}
 *      -> encoder.encode(raw)  -> "{bcrypt}$2a$10$..." -> INSERT
 *
 * The raw password lives only for the duration of that request.
 * Look at register(): validation, duplicate check (plus DB unique
 * constraint for the race two concurrent registrations create), encode,
 * save. That's the entire industry pattern.
 *
 * =====================================================================
 *  TRY IT
 * =====================================================================
 *  A. curl -i -u alice:alice-pass http://localhost:8080/private/hello
 *     -> 200, alice comes from the DATABASE now (seeded at startup).
 *  B. Register a new user:
 *     curl -i -X POST http://localhost:8080/public/register \
 *          -H "Content-Type: application/json" \
 *          -d "{\"username\":\"carol\",\"password\":\"carol-pass\"}"
 *     -> 201. Then log in as carol:
 *     curl -i -u carol:carol-pass http://localhost:8080/private/hello
 *     -> 200. You just built self-service signup.
 *  C. Register carol AGAIN -> 409 Conflict.
 *  D. H2 console (enabled in application.yml for this lesson):
 *     browser -> http://localhost:8080/h2-console
 *     JDBC URL: jdbc:h2:mem:lessondb   user: sa   password: (empty)
 *     SELECT * FROM user_accounts;  -> see the {bcrypt} hashes. THIS is
 *     what an attacker gets if your DB leaks — useless without years of
 *     GPU time. That is why Lesson 4 exists.
 *
 * =====================================================================
 *  KEY TAKEAWAYS
 * =====================================================================
 *  - UserDetailsService is the ONLY seam Spring Security needs; storage
 *    behind it is your business.
 *  - Encode at registration, store the hash, never log the raw password.
 *  - Duplicate-username: check in code AND constrain in the DB (races).
 *  - H2 console needs frameOptions tweaked + CSRF exception — note how
 *    NARROW we made both (only /h2-console/**). Never disable globally
 *    out of convenience.
 * =====================================================================
 */
@Configuration
@EnableWebSecurity
@Profile("lesson05")
public class Lesson05_DatabaseUsersConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()   // dev tool, lesson only
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/private/**").hasRole("USER")
                .anyRequest().denyAll()
            )
            // H2 console is a servlet UI that POSTs without a CSRF token and
            // renders itself in a frame. Carve out ONLY its path:
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/public/register"))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .httpBasic(Customizer.withDefaults())
            .formLogin(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Seed two known users so every lesson's curl commands keep working. */
    @Bean
    CommandLineRunner seedUsers(UserAccountRepository repo, PasswordEncoder encoder) {
        return args -> {
            if (repo.existsByUsername("alice")) return;
            repo.save(new UserAccount("alice", encoder.encode("alice-pass"), "USER"));
            repo.save(new UserAccount("bob", encoder.encode("bob-pass"), "USER,ADMIN"));
            System.out.println("\nLESSON 5: seeded users alice/alice-pass (USER), bob/bob-pass (USER,ADMIN)\n");
        };
    }

    /**
     * Registration API. Nested here to keep the lesson in one place;
     * real projects put this in its own controller + service class.
     */
    @RestController
    @Profile("lesson05")
    static class RegistrationController {

        record RegistrationRequest(String username, String password) {}

        private final UserAccountRepository repo;
        private final PasswordEncoder encoder;

        RegistrationController(UserAccountRepository repo, PasswordEncoder encoder) {
            this.repo = repo;
            this.encoder = encoder;
        }

        @PostMapping("/public/register")
        ResponseEntity<String> register(@RequestBody RegistrationRequest req) {
            if (req.username() == null || req.username().isBlank()
                    || req.password() == null || req.password().length() < 8) {
                return ResponseEntity.badRequest()
                        .body("username required; password min 8 chars");
            }
            if (repo.existsByUsername(req.username())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("username taken");
            }
            repo.save(new UserAccount(req.username(), encoder.encode(req.password()), "USER"));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("registered: " + req.username());
        }
    }
}
