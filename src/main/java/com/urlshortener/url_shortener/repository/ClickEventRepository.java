package com.urlshortener.url_shortener.repository;

import com.urlshortener.url_shortener.entity.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByShortCode(String shortCode);

    @Query("SELECT COUNT(DISTINCT c.ipAddress) FROM ClickEvent c WHERE c.shortCode = :code")
    long countUniqueIpsByShortCode(@Param("code") String shortCode);

    @Query("""
        SELECT c.country, COUNT(c) as cnt
        FROM ClickEvent c
        WHERE c.shortCode = :code
        GROUP BY c.country
        ORDER BY cnt DESC
        """)
    List<Object[]> countClicksByCountry(@Param("code") String shortCode);

    @Query("""
        SELECT CAST(c.clickedAt AS date), COUNT(c)
        FROM ClickEvent c
        WHERE c.shortCode = :code AND c.clickedAt >= :since
        GROUP BY CAST(c.clickedAt AS date)
        ORDER BY CAST(c.clickedAt AS date)
        """)
    List<Object[]> countClicksByDay(@Param("code") String shortCode,
                                    @Param("since") Instant since);
}