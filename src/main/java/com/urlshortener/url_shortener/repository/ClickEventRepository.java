package com.urlshortener.url_shortener.repository;

import com.urlshortener.url_shortener.entity.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    // ── Per-link queries ──────────────────────────────────────────────────────

    long countByShortCode(String shortCode);

    @Query("SELECT COUNT(c) FROM ClickEvent c WHERE c.shortCode = :code AND c.clickedAt >= :since")
    long countClicksSince(@Param("code") String shortCode, @Param("since") Instant since);

    @Query("SELECT COUNT(DISTINCT c.ipAddress) FROM ClickEvent c WHERE c.shortCode = :code AND c.clickedAt >= :since")
    long countUniqueIpsSince(@Param("code") String shortCode, @Param("since") Instant since);

    @Query("""
        SELECT c.country, COUNT(c) AS cnt
        FROM ClickEvent c
        WHERE c.shortCode = :code AND c.clickedAt >= :since
        GROUP BY c.country
        ORDER BY cnt DESC
        """)
    List<Object[]> countClicksByCountrySince(@Param("code") String shortCode,
                                             @Param("since") Instant since);

    @Query("""
        SELECT CAST(c.clickedAt AS date), COUNT(c)
        FROM ClickEvent c
        WHERE c.shortCode = :code AND c.clickedAt >= :since
        GROUP BY CAST(c.clickedAt AS date)
        ORDER BY CAST(c.clickedAt AS date)
        """)
    List<Object[]> countClicksByDay(@Param("code") String shortCode,
                                    @Param("since") Instant since);

    @Query("""
        SELECT c.deviceType, COUNT(c) AS cnt
        FROM ClickEvent c
        WHERE c.shortCode = :code AND c.clickedAt >= :since
        GROUP BY c.deviceType
        ORDER BY cnt DESC
        """)
    List<Object[]> countClicksByDeviceSince(@Param("code") String shortCode,
                                            @Param("since") Instant since);

    @Query("""
        SELECT COALESCE(c.referrer, 'Direct'), COUNT(c) AS cnt
        FROM ClickEvent c
        WHERE c.shortCode = :code AND c.clickedAt >= :since
        GROUP BY c.referrer
        ORDER BY cnt DESC
        """)
    List<Object[]> countClicksByReferrerSince(@Param("code") String shortCode,
                                              @Param("since") Instant since);

    // ── User-scoped dashboard queries ─────────────────────────────────────────

    @Query("SELECT COUNT(c) FROM ClickEvent c WHERE c.shortCode IN :codes AND c.clickedAt >= :since")
    long countTotalClicksForCodes(@Param("codes") Collection<String> codes,
                                  @Param("since") Instant since);

    @Query("SELECT COUNT(DISTINCT c.ipAddress) FROM ClickEvent c WHERE c.shortCode IN :codes AND c.clickedAt >= :since")
    long countUniqueVisitorsForCodes(@Param("codes") Collection<String> codes,
                                     @Param("since") Instant since);

    @Query("""
        SELECT c.country, COUNT(c) AS cnt
        FROM ClickEvent c
        WHERE c.shortCode IN :codes AND c.clickedAt >= :since
        GROUP BY c.country
        ORDER BY cnt DESC
        """)
    List<Object[]> countClicksByCountryForCodes(@Param("codes") Collection<String> codes,
                                                @Param("since") Instant since);

    @Query("""
        SELECT CAST(c.clickedAt AS date), COUNT(c)
        FROM ClickEvent c
        WHERE c.shortCode IN :codes AND c.clickedAt >= :since
        GROUP BY CAST(c.clickedAt AS date)
        ORDER BY CAST(c.clickedAt AS date)
        """)
    List<Object[]> countClicksByDayForCodes(@Param("codes") Collection<String> codes,
                                            @Param("since") Instant since);

    @Query("""
        SELECT c.deviceType, COUNT(c) AS cnt
        FROM ClickEvent c
        WHERE c.shortCode IN :codes AND c.clickedAt >= :since
        GROUP BY c.deviceType
        ORDER BY cnt DESC
        """)
    List<Object[]> countClicksByDeviceForCodes(@Param("codes") Collection<String> codes,
                                               @Param("since") Instant since);

    @Query("""
        SELECT COALESCE(c.referrer, 'Direct'), COUNT(c) AS cnt
        FROM ClickEvent c
        WHERE c.shortCode IN :codes AND c.clickedAt >= :since
        GROUP BY c.referrer
        ORDER BY cnt DESC
        """)
    List<Object[]> countClicksByReferrerForCodes(@Param("codes") Collection<String> codes,
                                                 @Param("since") Instant since);

    // ── Batch click counts (used by getUserUrls to avoid N+1) ─────────────────

    @Query("""
        SELECT c.shortCode, COUNT(c)
        FROM ClickEvent c
        WHERE c.shortCode IN :codes
        GROUP BY c.shortCode
        """)
    List<Object[]> countClicksPerCode(@Param("codes") Collection<String> codes);

    // ── Purge ─────────────────────────────────────────────────────────────────

    @Modifying
    @Query("DELETE FROM ClickEvent c WHERE c.shortCode IN :shortCodes")
    int deleteByShortCodeIn(@Param("shortCodes") List<String> shortCodes);

    // ── Prior-period totals for change % calculation ──────────────────────────

    @Query("""
        SELECT COUNT(c) FROM ClickEvent c
        WHERE c.shortCode IN :codes
        AND c.clickedAt >= :from AND c.clickedAt < :to
        """)
    long countClicksForCodesBetween(@Param("codes") Collection<String> codes,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);
}
