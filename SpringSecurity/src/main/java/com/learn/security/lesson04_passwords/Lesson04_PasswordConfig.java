package com.learn.security.lesson04_passwords;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * =====================================================================
 *  LESSON 4 — PASSWORD STORAGE DONE RIGHT ({noop} dies today)
 * =====================================================================
 *
 * Run:  mvn spring-boot:run -Dspring-boot.run.profiles=lesson04
 *
 * WHY YOU NEVER STORE PLAIN PASSWORDS
 * -----------------------------------
 * Assume your database WILL leak one day (backup stolen, SQL injection,
 * disgruntled employee). If passwords are plain text, the attacker owns
 * every account instantly — and because humans reuse passwords, their
 * bank accounts too. So we store something derived from the password
 * that cannot be reversed: a HASH.
 *
 * WHY NOT MD5 / SHA-256?
 * ----------------------
 * Those are FAST hashes, designed for checksums. Fast = attacker can try
 * billions of guesses per second on a GPU. Also, without a salt, two
 * users with password "123456" get the SAME hash, and precomputed
 * "rainbow tables" reverse common passwords in seconds.
 *
 * WHAT GOOD LOOKS LIKE: BCrypt (also: scrypt, argon2)
 * ---------------------------------------------------
 *   1. SLOW ON PURPOSE. Cost factor 10 = 2^10 internal rounds, ~100ms
 *      per check. Irrelevant for one login, catastrophic for a guesser.
 *      Hardware gets faster? Raise the cost factor. It's future-proof.
 *   2. SALTED AUTOMATICALLY. Random salt generated per password, stored
 *      INSIDE the hash string. Same password -> different hash every
 *      time. Rainbow tables dead. The runner below proves it.
 *
 * ANATOMY OF A BCRYPT STRING
 * --------------------------
 *   $2a$10$N9qo8uLOickgx2ZMRZoMye.IjPeGvGzjz1Pgm8V5V5x9uS7dEbW0W
 *   ^^^ ^^^ ^^^^^^^^^^^^^^^^^^^^^^ ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
 *   alg cost      22-char salt              31-char hash
 * Salt is PUBLIC — its job is uniqueness, not secrecy.
 *
 * DelegatingPasswordEncoder — the {prefix} mystery solved
 * -------------------------------------------------------
 * PasswordEncoderFactories.createDelegatingPasswordEncoder() returns an
 * encoder that WRITES "{bcrypt}<hash>" and, when CHECKING, reads the
 * prefix to pick the right algorithm:
 *    "{noop}x"     -> plain comparison   (how Lessons 2-3 worked)
 *    "{bcrypt}..." -> BCrypt
 *    "{argon2}..." -> Argon2
 * Why? MIGRATION. Real systems have 10-year-old rows hashed with old
 * algorithms. The prefix lets old and new coexist, and you can re-hash
 * each user's password on their next successful login. Industry answer
 * to "how do we upgrade hashing without resetting everyone's password".
 *
 * THE FLOW AT LOGIN
 * -----------------
 *   1. UserDetailsService loads stored "{bcrypt}$2a$10$..." for alice
 *   2. DaoAuthenticationProvider calls
 *          passwordEncoder.matches(submittedPassword, storedHash)
 *      -> hashes the submitted password WITH THE SALT EXTRACTED FROM
 *         THE STORED HASH, compares results.
 *   The plain password is never stored, never logged, compared once
 *   in memory, then discarded.
 *
 * =====================================================================
 *  TRY IT
 * =====================================================================
 *  A. Startup log: runner prints "alice-pass" encoded TWICE. Different
 *     output each time (random salt) — yet both validate. Restart app:
 *     different again.
 *  B. curl -i -u alice:alice-pass http://localhost:8080/private/hello
 *     -> 200. Login works exactly as before; only STORAGE changed.
 *  C. Time it: BCrypt check costs ~50-100ms. Try cost 14 in the runner
 *     (BCryptPasswordEncoder(14)) and feel the delay grow 16x.
 *
 * =====================================================================
 *  KEY TAKEAWAYS
 * =====================================================================
 *  - Store slow, salted hashes (BCrypt/Argon2). Never plain, never MD5/SHA.
 *  - Salt is public, per-password, kills rainbow tables.
 *  - DelegatingPasswordEncoder + {prefix} = algorithm migration path.
 *  - matches(raw, encoded) — you never decode; you re-hash and compare.
 * =====================================================================
 */
@Configuration
@EnableWebSecurity
@Profile("lesson04")
public class Lesson04_PasswordConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/private/**").hasRole("USER")
                .anyRequest().denyAll()
            )
            .httpBasic(Customizer.withDefaults())
            .formLogin(Customizer.withDefaults());
        return http.build();
    }

    /**
     * THE bean of this lesson. Once a PasswordEncoder bean exists,
     * DaoAuthenticationProvider uses it for every password check.
     * Factory method = DelegatingPasswordEncoder with bcrypt as the
     * default for NEW passwords.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Passwords now encoded at startup — no {noop} anywhere.
     * (Encoding at startup is still demo-grade: real systems encode at
     * REGISTRATION time and store the hash. That's Lesson 5.)
     */
    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails alice = User.builder()
                .username("alice")
                .password(encoder.encode("alice-pass"))  // -> "{bcrypt}$2a$10$..."
                .roles("USER")
                .build();

        UserDetails bob = User.builder()
                .username("bob")
                .password(encoder.encode("bob-pass"))
                .roles("USER", "ADMIN")
                .build();

        return new InMemoryUserDetailsManager(alice, bob);
    }

    /** Proof of salting: same input, different hash, both valid. */
    @Bean
    CommandLineRunner demonstrateSalting(PasswordEncoder encoder) {
        return args -> {
            String h1 = encoder.encode("alice-pass");
            String h2 = encoder.encode("alice-pass");
            System.out.println("\n========== LESSON 4: SALTING DEMO ==========");
            System.out.println("encode(\"alice-pass\") #1: " + h1);
            System.out.println("encode(\"alice-pass\") #2: " + h2);
            System.out.println("hashes equal?            " + h1.equals(h2) + "   (salt makes them differ)");
            System.out.println("matches #1?              " + encoder.matches("alice-pass", h1));
            System.out.println("matches #2?              " + encoder.matches("alice-pass", h2));
            System.out.println("============================================\n");
        };
    }
}
