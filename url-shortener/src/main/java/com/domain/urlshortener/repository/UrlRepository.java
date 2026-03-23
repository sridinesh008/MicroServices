package com.domain.urlshortener.repository;

import com.domain.urlshortener.model.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<UrlMapping, Long> {

    @Query("SELECT u FROM UrlMapping u WHERE u.shortCode = :code " +
           "AND (u.expiresAt IS NULL OR u.expiresAt > :now)")
    Optional<UrlMapping> findActiveByShortCode(@Param("code") String code,
                                               @Param("now") LocalDateTime now);

    boolean existsByShortCode(String shortCode);

    @Modifying
    @Query("UPDATE UrlMapping u SET u.clickCount = u.clickCount + 1 WHERE u.shortCode = :code")
    void incrementClickCount(@Param("code") String code);
}
