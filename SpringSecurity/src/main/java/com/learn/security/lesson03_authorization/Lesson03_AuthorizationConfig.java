package com.learn.security.lesson03_authorization;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
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
 *  LESSON 3 — AUTHORIZATION: ROLES, AUTHORITIES, HIERARCHY, 401 vs 403
 * =====================================================================
 *
 * Run:  mvn spring-boot:run -Dspring-boot.run.profiles=lesson03
 *
 * Lesson 2 ended with a hole: alice (role USER) could call /admin/hello,
 * because our only rule was "authenticated". This lesson closes it.
 *
 * AUTHENTICATION vs AUTHORIZATION — get this straight once
 * --------------------------------------------------------
 *   Authentication  = WHO are you?         (login, credentials)
 *   Authorization   = WHAT may you do?     (roles, permissions)
 * Security failures map to different HTTP codes:
 *   401 Unauthorized = "I don't know who you are" (misnamed header-era
 *                      status; really means UNAUTHENTICATED)
 *   403 Forbidden    = "I know exactly who you are — and no."
 * ExceptionTranslationFilter (Lesson 1 chain, position 15) decides which
 * one to send. You will see BOTH in TRY IT below.
 *
 * ROLES vs AUTHORITIES — same thing, one prefix apart
 * ---------------------------------------------------
 * Internally Spring Security knows only ONE concept: GrantedAuthority,
 * a plain string. "Role" is just a NAMING CONVENTION on top:
 *
 *   .roles("ADMIN")              stores authority  "ROLE_ADMIN"
 *   .authorities("ROLE_ADMIN")   stores exactly the same thing
 *
 *   hasRole("ADMIN")        checks for authority "ROLE_ADMIN"
 *   hasAuthority("ROLE_ADMIN")   identical check
 *
 * Convention: coarse-grained job titles -> roles (ADMIN, USER, AUDITOR);
 * fine-grained permissions -> plain authorities (invoice:read,
 * invoice:write). Big systems use both: roles for URL rules, permissions
 * for method rules (Lesson 6).
 * Classic bug: writing hasRole("ROLE_ADMIN") — checks "ROLE_ROLE_ADMIN",
 * never matches, nobody gets in. hasRole adds the prefix FOR you.
 *
 * ROLE HIERARCHY — stop listing every role everywhere
 * ---------------------------------------------------
 * Natural expectation: admin can do everything a user can. Without help
 * you'd write hasAnyRole("USER","ADMIN") on every user endpoint — noisy,
 * and adding a role means editing every rule. Instead declare once:
 *
 *   ADMIN implies USER
 *
 * The RoleHierarchy bean below does that. Spring Security 6.3+ picks the
 * bean up automatically for both URL rules and method security. Result:
 * bob has ONLY role ADMIN in his user record, yet passes hasRole("USER")
 * checks. Proof in TRY IT (D).
 *
 * THE RULES BELOW, LINE BY LINE
 * -----------------------------
 *   /public/**  -> permitAll        anyone, even anonymous
 *   /admin/**   -> hasRole("ADMIN") the fix for Lesson 2's hole
 *   /private/** -> hasRole("USER")  note: bob passes via hierarchy
 *   anyRequest  -> denyAll          NEW! Stricter than authenticated().
 *
 * Why denyAll as catch-all? Production habit: every endpoint must be
 * EXPLICITLY listed to be reachable. A new controller added next sprint
 * is dead until someone consciously writes a rule for it. "Secure by
 * default" applied to your own code. (authenticated() as catch-all is
 * fine too — a deliberate, documented choice; denyAll is the paranoid
 * end of the spectrum. Know both, choose per system.)
 *
 * ORDER STILL MATTERS. First match wins. If /public/** came AFTER a
 * hypothetical /** rule, it would never be reached.
 *
 * =====================================================================
 *  TRY IT
 * =====================================================================
 *
 *  A. curl -i http://localhost:8080/admin/hello
 *     -> 401. Anonymous. "Who ARE you?"
 *
 *  B. curl -i -u alice:alice-pass http://localhost:8080/admin/hello
 *     -> 403. THE HOLE IS CLOSED. Alice authenticated fine — then the
 *        AuthorizationFilter checked hasRole("ADMIN") and refused.
 *        Compare the response code with A. 401 vs 403. Feel the difference.
 *
 *  C. curl -i -u bob:bob-pass http://localhost:8080/admin/hello
 *     -> 200. bob has ROLE_ADMIN.
 *
 *  D. curl -i -u bob:bob-pass http://localhost:8080/private/hello
 *     -> 200. Rule says hasRole("USER") and bob's record has ONLY
 *        "ADMIN" — hierarchy made ADMIN imply USER. Comment out the
 *        roleHierarchy() bean, restart, repeat -> 403. Uncomment.
 *
 *  E. curl -i -u alice:alice-pass http://localhost:8080/actuator/health
 *     (or any URL not in the rules, e.g. /whatever)
 *     -> 403 even though alice is logged in. denyAll catch-all at work:
 *        unlisted = unreachable.
 *
 * =====================================================================
 *  KEY TAKEAWAYS
 * =====================================================================
 *  - 401 = unauthenticated, 403 = authenticated but not allowed.
 *  - Role = authority with "ROLE_" prefix. hasRole adds the prefix;
 *    never write it yourself.
 *  - RoleHierarchy: declare ADMIN > USER once instead of hasAnyRole
 *    everywhere.
 *  - denyAll catch-all = new endpoints are dead until explicitly opened.
 * =====================================================================
 */
@Configuration
@EnableWebSecurity
@Profile("lesson03")
public class Lesson03_AuthorizationConfig {

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
     * ADMIN implies USER. Declared once, applies everywhere.
     * Spring Security 6.3+ auto-detects this bean in URL rules;
     * it will also apply to @PreAuthorize in Lesson 6.
     */
    @Bean
    RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("USER")
                .build();
    }

    /**
     * Same two users as Lesson 2 — but LOOK at bob: only role ADMIN.
     * No USER role anywhere in his record. TRY IT (D) proves the
     * hierarchy grants it to him anyway.
     */
    @Bean
    UserDetailsService userDetailsService() {
        UserDetails alice = User.builder()
                .username("alice")
                .password("{noop}alice-pass")   // still {noop}: Lesson 4 is next
                .roles("USER")
                .build();

        UserDetails bob = User.builder()
                .username("bob")
                .password("{noop}bob-pass")
                .roles("ADMIN")                 // ONLY admin. USER comes from hierarchy.
                .build();

        return new InMemoryUserDetailsManager(alice, bob);
    }
}
