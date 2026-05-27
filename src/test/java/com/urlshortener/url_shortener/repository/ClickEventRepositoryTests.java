package com.urlshortener.url_shortener.repository;

import com.urlshortener.url_shortener.entity.ClickEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional
@ActiveProfiles("test")
public class ClickEventRepositoryTests {

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Autowired
    private EntityManager entityManager;

    // Constants

    private static final String SHORT_CODE = "abc123";
    private static final String OTHER_SHORT_CODE = "xyz999";

    private static final Instant JAN_2020 = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant JAN_2026 = Instant.parse("2026-01-01T00:00:00Z");

    // Helpers

    private ClickEvent buildClickEvent(String shortCode, String ipAddress, String country, Instant clickedAt) {
        return ClickEvent.builder()
                .shortCode(shortCode)
                .ipAddress(ipAddress)
                .country(country)
                .clickedAt(clickedAt)
                .build();
    }

    /**
     * Native insert helper used specifically for timestamp-based tests.
     * This bypasses @CreationTimestamp / @PrePersist logic.
     */
    private void insertClickEvent(String shortCode, String ipAddress, String country, Instant clickedAt) {
        entityManager.createNativeQuery("""
                INSERT INTO click_events
                (short_code, ip_address, country, clicked_at, device_type)
                VALUES (?, ?, ?, ?, ?)
                """)
                .setParameter(1, shortCode)
                .setParameter(2, ipAddress)
                .setParameter(3, country)
                .setParameter(4, Timestamp.from(clickedAt))
                .setParameter(5, "DESKTOP")
                .executeUpdate();
    }

    private Map<String, Long> mapCountryCounts(List<Object[]> results) {
        return results.stream()
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (Long) r[1]
                ));
    }

    private Map<LocalDate, Long> mapDailyCounts(List<Object[]> results) {
        return results.stream()
                .collect(Collectors.toMap(
                        r -> ((Date) r[0]).toLocalDate(),
                        r -> (Long) r[1]
                ));
    }

    // countByShortCode

    @Test
    @DisplayName("countByShortCode returns total click count")
    public void ClickEventRepository_countByShortCode_returnsTotalClickCount() {

        // Arrange
        clickEventRepository.saveAllAndFlush(List.of(
                buildClickEvent(SHORT_CODE, "192.168.1.1", "IN", JAN_2020),
                buildClickEvent(SHORT_CODE, "192.168.1.2", "IN", JAN_2020)
        ));

        // Act
        long count = clickEventRepository.countByShortCode(SHORT_CODE);

        // Assert
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("countByShortCode returns zero when shortcode does not exist")
    public void ClickEventRepository_countByShortCode_returnsZeroIfShortCodeDoesNotExist() {
        assertThat(clickEventRepository.countByShortCode("unknown")).isZero();
    }

    @Test
    @DisplayName("countByShortCode does not count other shortcodes")
    public void ClickEventRepository_countByShortCode_doesNotCountOtherShortCodes() {

        // Arrange
        clickEventRepository.saveAndFlush(
                buildClickEvent(OTHER_SHORT_CODE, "192.168.1.1", "IN", JAN_2020)
        );

        // Act & Assert
        assertThat(clickEventRepository.countByShortCode(SHORT_CODE)).isZero();
    }

    // countUniqueIpsByShortCode

    @Test
    @DisplayName("countUniqueIpsByShortCode returns unique IP count")
    public void ClickEventRepository_countUniqueIpsByShortCode_returnsUniqueIpCount() {

        // Arrange
        clickEventRepository.saveAllAndFlush(List.of(
                buildClickEvent(SHORT_CODE, "192.168.1.1", "IN", JAN_2020), // duplicate IP
                buildClickEvent(SHORT_CODE, "192.168.1.1", "IN", JAN_2020), // duplicate IP
                buildClickEvent(SHORT_CODE, "192.168.1.2", "US", JAN_2020)
        ));

        // Act
        long uniqueCount = clickEventRepository.countUniqueIpsByShortCode(SHORT_CODE);

        // Assert
        assertThat(uniqueCount).isEqualTo(2);
    }

    @Test
    @DisplayName("countUniqueIpsByShortCode returns zero when no clicks exist")
    public void ClickEventRepository_countUniqueIpsByShortCode_returnsZeroIfNoClicksExist() {
        assertThat(clickEventRepository.countUniqueIpsByShortCode("unknown")).isZero();
    }

    @Test
    @DisplayName("countUniqueIpsByShortCode does not count other shortcodes")
    public void ClickEventRepository_countUniqueIpsByShortCode_doesNotCountOtherShortCodes() {

        // Arrange
        clickEventRepository.saveAndFlush(
                buildClickEvent(OTHER_SHORT_CODE, "192.168.1.1", "IN", JAN_2020)
        );

        // Act & Assert
        assertThat(clickEventRepository.countUniqueIpsByShortCode(SHORT_CODE)).isZero();
    }

    // countClicksByCountry

    @Test
    @DisplayName("countClicksByCountry returns grouped country counts")
    public void ClickEventRepository_countClicksByCountry_returnsGroupedCountryCounts() {

        // Arrange
        clickEventRepository.saveAllAndFlush(List.of(
                buildClickEvent(SHORT_CODE, "192.168.1.1", "IN", JAN_2020),
                buildClickEvent(SHORT_CODE, "192.168.1.2", "IN", JAN_2020),
                buildClickEvent(SHORT_CODE, "192.168.1.3", "US", JAN_2020)
        ));

        // Act
        Map<String, Long> countsByCountry =
                mapCountryCounts(clickEventRepository.countClicksByCountry(SHORT_CODE));

        // Assert
        assertThat(countsByCountry)
                .containsExactlyInAnyOrderEntriesOf(Map.of("IN", 2L, "US", 1L));
    }

    @Test
    @DisplayName("countClicksByCountry returns empty list when no clicks exist")
    public void ClickEventRepository_countClicksByCountry_returnsEmptyListIfNoClicksExist() {
        assertThat(clickEventRepository.countClicksByCountry("unknown")).isEmpty();
    }

    @Test
    @DisplayName("countClicksByCountry does not count other shortcodes")
    public void ClickEventRepository_countClicksByCountry_doesNotCountOtherShortCodes() {

        // Arrange
        clickEventRepository.saveAndFlush(
                buildClickEvent(OTHER_SHORT_CODE, "192.168.1.1", "IN", JAN_2020)
        );

        // Act & Assert
        assertThat(clickEventRepository.countClicksByCountry(SHORT_CODE)).isEmpty();
    }

    // countClicksByDay

    @Test
    @DisplayName("countClicksByDay returns grouped daily click counts")
    public void ClickEventRepository_countClicksByDay_returnsGroupedDailyClickCounts() {

        // Arrange
        insertClickEvent(SHORT_CODE, "192.168.1.1", "IN", JAN_2020);
        insertClickEvent(SHORT_CODE, "192.168.1.2", "IN", JAN_2020);
        insertClickEvent(SHORT_CODE, "192.168.1.3", "US", JAN_2026);

        // Act
        Map<LocalDate, Long> countsByDay =
                mapDailyCounts(clickEventRepository.countClicksByDay(SHORT_CODE, JAN_2020));

        // Assert
        assertThat(countsByDay)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        LocalDate.of(2020, 1, 1), 2L,
                        LocalDate.of(2026, 1, 1), 1L
                ));
    }

    @Test
    @DisplayName("countClicksByDay excludes clicks before since date")
    public void ClickEventRepository_countClicksByDay_excludesClicksBeforeSinceDate() {

        // Arrange — click is in 2020, query starts from 2026
        insertClickEvent(SHORT_CODE, "192.168.1.1", "IN", JAN_2020);

        // Act & Assert
        assertThat(clickEventRepository.countClicksByDay(SHORT_CODE, JAN_2026)).isEmpty();
    }

    @Test
    @DisplayName("countClicksByDay includes clicks exactly at since date (>= boundary)")
    public void ClickEventRepository_countClicksByDay_includesClicksAtExactSinceDate() {

        // Arrange — click is inserted exactly at JAN_2026
        insertClickEvent(SHORT_CODE, "192.168.1.1", "IN", JAN_2026);

        // Act
        Map<LocalDate, Long> countsByDay =
                mapDailyCounts(clickEventRepository.countClicksByDay(SHORT_CODE, JAN_2026));

        // Assert — the boundary click must be included
        assertThat(countsByDay)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        LocalDate.of(2026, 1, 1), 1L
                ));
    }

    @Test
    @DisplayName("countClicksByDay does not count other shortcodes")
    public void ClickEventRepository_countClicksByDay_doesNotCountOtherShortCodes() {

        // Arrange
        insertClickEvent(OTHER_SHORT_CODE, "192.168.1.1", "IN", JAN_2020);

        // Act & Assert
        assertThat(clickEventRepository.countClicksByDay(SHORT_CODE, JAN_2020)).isEmpty();
    }

    // Validation / Constraint Tests

    @Test
    @DisplayName("save throws exception when shortCode is null")
    public void ClickEventRepository_save_throwsException_whenShortCodeIsNull() {

        assertThatThrownBy(() ->
                clickEventRepository.saveAndFlush(
                        buildClickEvent(null, "192.168.1.1", "IN", JAN_2020)
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("save throws exception when ipAddress is null")
    public void ClickEventRepository_save_throwsException_whenIpAddressIsNull() {

        assertThatThrownBy(() ->
                clickEventRepository.saveAndFlush(
                        buildClickEvent(SHORT_CODE, null, "IN", JAN_2020)
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("save throws exception when country is null")
    public void ClickEventRepository_save_throwsException_whenCountryIsNull() {

        assertThatThrownBy(() ->
                clickEventRepository.saveAndFlush(
                        buildClickEvent(SHORT_CODE, "192.168.1.1", null, JAN_2020)
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

}