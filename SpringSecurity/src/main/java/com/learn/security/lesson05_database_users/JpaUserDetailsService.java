package com.learn.security.lesson05_database_users;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * LESSON 5 (part 3/4) — THE BRIDGE between your database and Spring Security.
 *
 * Same interface as the InMemoryUserDetailsManager from Lessons 2-4 —
 * that was the promise: swap the storage, keep the contract. Spring
 * Security neither knows nor cares that a database is behind this now.
 *
 * WHAT HAPPENS ON LOGIN (full flow, memorize this):
 *
 *   1. BasicAuthenticationFilter (or the form login filter) extracts
 *      username + password from the request
 *   2. hands them to AuthenticationManager -> DaoAuthenticationProvider
 *   3. DaoAuthenticationProvider calls THIS method with the username
 *   4. we load the row and map it to a UserDetails
 *   5. provider calls passwordEncoder.matches(submitted, stored hash)
 *   6. match -> Authentication object goes into the SecurityContext;
 *      no match -> BadCredentialsException -> 401
 *
 * SECURITY DETAIL: when the username does not exist we throw
 * UsernameNotFoundException, but Spring Security deliberately reports
 * the SAME generic "Bad credentials" to the client either way. Telling
 * an attacker "user exists but wrong password" lets them harvest valid
 * usernames (user enumeration attack).
 */
@Service
@Profile("lesson05")
public class JpaUserDetailsService implements UserDetailsService {

    private final UserAccountRepository repository;

    public JpaUserDetailsService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount account = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user: " + username));

        return User.builder()
                .username(account.getUsername())
                .password(account.getPassword())             // already the encoded hash
                .roles(account.getRoles().split(","))        // "USER,ADMIN" -> ROLE_USER, ROLE_ADMIN
                .disabled(!account.isEnabled())
                .build();
    }
}
