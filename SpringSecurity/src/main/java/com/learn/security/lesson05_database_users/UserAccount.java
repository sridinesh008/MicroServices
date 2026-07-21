package com.learn.security.lesson05_database_users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * LESSON 5 (part 1/4) — the database row for a user.
 *
 * Plain JPA entity. Nothing Spring-Security-specific here — and that is
 * the lesson: Spring Security does NOT dictate your user table. Any
 * schema works, as long as your UserDetailsService (part 3) can map a
 * row to a UserDetails object.
 *
 * Design notes worth copying into real projects:
 *  - `password` column stores the ENCODED hash ("{bcrypt}$2a$10$..."),
 *    written once at registration. Plain password never touches disk.
 *  - `roles` here is a CSV string ("USER,ADMIN") for simplicity. Real
 *    systems usually use a separate roles table (@ManyToMany) — the
 *    mapping code in part 3 is the only thing that would change.
 *  - unique constraint on username: enforce identity at the DB level,
 *    not only in application code (two concurrent registrations!).
 */
@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    /** BCrypt hash with {bcrypt} prefix — NEVER the raw password. */
    @Column(nullable = false)
    private String password;

    /** CSV of role names WITHOUT the ROLE_ prefix, e.g. "USER,ADMIN". */
    @Column(nullable = false)
    private String roles;

    private boolean enabled = true;

    protected UserAccount() { /* JPA needs a no-arg constructor */ }

    public UserAccount(String username, String encodedPassword, String roles) {
        this.username = username;
        this.password = encodedPassword;
        this.roles = roles;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getRoles() { return roles; }
    public boolean isEnabled() { return enabled; }
    public void setPassword(String encodedPassword) { this.password = encodedPassword; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
