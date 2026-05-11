package com.urlshortener.url_shortener.repository;

import com.urlshortener.url_shortener.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortCodeAndActiveTrue(String shortCode);

    Optional<Url> findByLongUrlAndUserIdAndActiveTrue(String longUrl, Long userId);

    boolean existsByShortCode(String shortCode);

    boolean existsByCustomAlias(String customAlias);

    Set<Url> findByUserIdAndActiveTrueOrderByCreatedAtDesc(Long userId);

    List<Url> findByUserEmailAndActiveTrue(String email);

    // Soft-delete expired URLs — used by cleanup worker
    @Modifying
    @Query("UPDATE Url u SET u.active = false WHERE u.expiresAt < :now AND u.active = true")
    int deactivateExpiredUrls(@Param("now") Instant now);

    // Fetch expired short codes for cache invalidation
    @Query("SELECT u.shortCode FROM Url u WHERE u.expiresAt < :now AND u.active = false AND u.expiresAt > :cutoff")
    List<String> findRecentlyExpiredCodes(@Param("now") Instant now,
                                          @Param("cutoff") Instant cutoff);

    @Query("SELECT u.shortCode FROM Url u")
    List<String> findAllShortCodes();

    @Query("SELECT u.shortCode FROM Url u WHERE u.active = true")
    List<String> findAllActiveShortCodes();
}

