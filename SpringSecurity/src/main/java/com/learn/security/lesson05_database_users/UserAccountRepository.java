package com.learn.security.lesson05_database_users;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * LESSON 5 (part 2/4) — standard Spring Data repository.
 *
 * findByUsername is the single query security needs: login gives us a
 * username, we fetch the row. Spring Data generates the SQL from the
 * method name.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);
}
