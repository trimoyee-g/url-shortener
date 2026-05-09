package com.urlshortener.url_shortener.repository;

import com.urlshortener.url_shortener.entity.Url;
import com.urlshortener.url_shortener.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional
@ActiveProfiles("test")
public class UrlRepositoryTests {

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private EntityManager entityManager;


    // ── Constants ────────────────────────────────────────────────────────────

    private static final Instant NOW    = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant FUTURE = NOW.plusSeconds(3600);
    private static final Instant PAST   = NOW.minusSeconds(3600);


    // ── Helpers ──────────────────────────────────────────────────────────────

    private User createAndPersistUser() {
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("password")
                .build();
        return entityManager.merge(user);
    }

    private User createAndPersistSecondUser() {
        User user = User.builder()
                .id(2L)
                .email("other@example.com")
                .password("password")
                .build();
        return entityManager.merge(user);
    }

    private Url buildUrl(Long id,
                         String shortCode,
                         String longUrl,
                         boolean active,
                         Instant expiresAt,
                         User user) {
        return Url.builder()
                .id(id)
                .shortCode(shortCode)
                .longUrl(longUrl)
                .user(user)
                .active(active)
                .expiresAt(expiresAt)
                .createdAt(NOW)
                .build();
    }

    private Url buildUrlWithAlias(Long id,
                                  String shortCode,
                                  String longUrl,
                                  String customAlias,
                                  boolean active,
                                  Instant expiresAt,
                                  User user) {
        return Url.builder()
                .id(id)
                .shortCode(shortCode)
                .longUrl(longUrl)
                .customAlias(customAlias)
                .user(user)
                .active(active)
                .expiresAt(expiresAt)
                .createdAt(NOW)
                .build();
    }


    // ── findByShortCodeAndActiveTrue ─────────────────────────────────────────

    @Nested
    @DisplayName("findByShortCodeAndActiveTrue")
    class FindByShortCodeAndActiveTrue {

        @Test
        @DisplayName("returns active url when shortCode matches")
        void returnsUrl_whenActiveAndExists() {
            User user = createAndPersistUser();
            urlRepository.saveAndFlush(
                    buildUrl(100L, "abc123", "https://google.com", true, FUTURE, user));

            Optional<Url> result = urlRepository.findByShortCodeAndActiveTrue("abc123");

            assertThat(result).isPresent();
            assertThat(result.get().getLongUrl()).isEqualTo("https://google.com");
        }

        @Test
        @DisplayName("returns empty when url exists but is inactive")
        void returnsEmpty_whenUrlIsInactive() {
            User user = createAndPersistUser();
            urlRepository.saveAndFlush(
                    buildUrl(101L, "inactive", "https://google.com", false, FUTURE, user));

            Optional<Url> result = urlRepository.findByShortCodeAndActiveTrue("inactive");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when shortCode does not exist")
        void returnsEmpty_whenShortCodeMissing() {
            Optional<Url> result = urlRepository.findByShortCodeAndActiveTrue("ghost");

            assertThat(result).isEmpty();
        }
    }


    // ── findByLongUrlAndUserIdAndActiveTrue ──────────────────────────────────

    @Nested
    @DisplayName("findByLongUrlAndUserIdAndActiveTrue")
    class FindByLongUrlAndUserIdAndActiveTrue {

        @Test
        @DisplayName("returns url when all three conditions match")
        void returnsUrl_whenAllConditionsMatch() {
            User user = createAndPersistUser();
            urlRepository.saveAndFlush(
                    buildUrl(110L, "xyz", "https://example.com", true, FUTURE, user));

            Optional<Url> result = urlRepository
                    .findByLongUrlAndUserIdAndActiveTrue("https://example.com", user.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getShortCode()).isEqualTo("xyz");
        }

        @Test
        @DisplayName("returns empty when url belongs to a different user")
        void returnsEmpty_whenWrongUser() {
            User user1 = createAndPersistUser();
            User user2 = createAndPersistSecondUser();
            urlRepository.saveAndFlush(
                    buildUrl(111L, "xyz", "https://example.com", true, FUTURE, user1));

            Optional<Url> result = urlRepository
                    .findByLongUrlAndUserIdAndActiveTrue("https://example.com", user2.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when url is inactive")
        void returnsEmpty_whenInactive() {
            User user = createAndPersistUser();
            urlRepository.saveAndFlush(
                    buildUrl(112L, "xyz", "https://example.com", false, FUTURE, user));

            Optional<Url> result = urlRepository
                    .findByLongUrlAndUserIdAndActiveTrue("https://example.com", user.getId());

            assertThat(result).isEmpty();
        }
    }


    // ── existsByShortCode ────────────────────────────────────────────────────

    @Nested
    @DisplayName("existsByShortCode")
    class ExistsByShortCode {

        @Test
        @DisplayName("returns true when shortCode exists")
        void returnsTrue_whenExists() {
            User user = createAndPersistUser();
            urlRepository.saveAndFlush(
                    buildUrl(120L, "exists", "url", true, FUTURE, user));

            assertThat(urlRepository.existsByShortCode("exists")).isTrue();
        }

        @Test
        @DisplayName("returns false when shortCode does not exist")
        void returnsFalse_whenMissing() {
            assertThat(urlRepository.existsByShortCode("ghost")).isFalse();
        }

        @Test
        @DisplayName("returns true even when url is inactive")
        void returnsTrue_evenWhenInactive() {
            User user = createAndPersistUser();
            urlRepository.saveAndFlush(
                    buildUrl(121L, "inactive", "url", false, FUTURE, user));

            // existsByShortCode has no active filter — it checks existence only
            assertThat(urlRepository.existsByShortCode("inactive")).isTrue();
        }
    }


    // ── existsByCustomAlias ──────────────────────────────────────────────────

    @Nested
    @DisplayName("existsByCustomAlias")
    class ExistsByCustomAlias {

        @Test
        @DisplayName("returns true when customAlias exists")
        void returnsTrue_whenAliasExists() {
            User user = createAndPersistUser();
            urlRepository.saveAndFlush(
                    buildUrlWithAlias(130L, "sc1", "url", "my-alias", true, FUTURE, user));

            assertThat(urlRepository.existsByCustomAlias("my-alias")).isTrue();
        }

        @Test
        @DisplayName("returns false when customAlias does not exist")
        void returnsFalse_whenAliasMissing() {
            assertThat(urlRepository.existsByCustomAlias("no-alias")).isFalse();
        }
    }


    // ── findByUserIdAndActiveTrueOrderByCreatedAtDesc ────────────────────────

    @Nested
    @DisplayName("findByUserIdAndActiveTrueOrderByCreatedAtDesc")
    class FindByUser {

        @Test
        @DisplayName("returns only active urls for the given user")
        void returnsActiveUrls_forUser() {
            User user = createAndPersistUser();
            urlRepository.saveAllAndFlush(List.of(
                    buildUrl(140L, "a1", "url1", true, FUTURE, user),
                    buildUrl(141L, "a2", "url2", true, FUTURE, user),
                    buildUrl(142L, "a3", "url3", false, FUTURE, user)  // inactive — must be excluded
            ));

            Set<Url> result = urlRepository
                    .findByUserIdAndActiveTrueOrderByCreatedAtDesc(user.getId());

            assertThat(result).hasSize(2);
            assertThat(result).extracting(Url::getShortCode)
                    .containsExactlyInAnyOrder("a1", "a2");
        }

        @Test
        @DisplayName("does not return urls belonging to a different user")
        void doesNotReturn_otherUsersUrls() {
            User user1 = createAndPersistUser();
            User user2 = createAndPersistSecondUser();

            urlRepository.saveAndFlush(
                    buildUrl(143L, "u1url", "url", true, FUTURE, user1));

            Set<Url> result = urlRepository
                    .findByUserIdAndActiveTrueOrderByCreatedAtDesc(user2.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty set when user has no urls")
        void returnsEmptySet_whenNoUrls() {
            User user = createAndPersistUser();

            Set<Url> result = urlRepository
                    .findByUserIdAndActiveTrueOrderByCreatedAtDesc(user.getId());

            assertThat(result).isEmpty();
        }
    }


    // ── deactivateExpiredUrls ────────────────────────────────────────────────

    @Nested
    @DisplayName("deactivateExpiredUrls")
    class DeactivateExpiredUrls {

        @Test
        @DisplayName("deactivates only expired urls")
        void deactivatesExpired_leavesActiveIntact() {
            User user = createAndPersistUser();
            urlRepository.saveAndFlush(buildUrl(150L, "old", "url", true, PAST, user));
            urlRepository.saveAndFlush(buildUrl(151L, "new", "url2", true, FUTURE, user));

            int updated = urlRepository.deactivateExpiredUrls(NOW);

            assertThat(updated).isEqualTo(1);
            assertThat(urlRepository.findByShortCodeAndActiveTrue("old")).isEmpty();
            assertThat(urlRepository.findByShortCodeAndActiveTrue("new")).isPresent();
        }

        @Test
        @DisplayName("returns 0 when no urls are expired")
        void returnsZero_whenNothingExpired() {
            User user = createAndPersistUser();
            urlRepository.saveAndFlush(buildUrl(152L, "fresh", "url", true, FUTURE, user));

            int updated = urlRepository.deactivateExpiredUrls(NOW);

            assertThat(updated).isEqualTo(0);
        }

        @Test
        @DisplayName("does not re-deactivate already inactive urls")
        void skipsAlreadyInactive() {
            User user = createAndPersistUser();
            urlRepository.saveAndFlush(buildUrl(153L, "already", "url", false, PAST, user));

            int updated = urlRepository.deactivateExpiredUrls(NOW);

            assertThat(updated).isEqualTo(0);
        }
    }


    // ── findRecentlyExpiredCodes ─────────────────────────────────────────────

    @Nested
    @DisplayName("findRecentlyExpiredCodes")
    class FindRecentlyExpiredCodes {

        @Test
        @DisplayName("returns short codes expired within the window")
        void returnsCodes_withinWindow() {
            User user = createAndPersistUser();
            Instant justExpired = NOW.minusSeconds(60);   // inside window
            Instant longExpired = NOW.minusSeconds(7200); // outside window (cutoff = NOW - 3600)

            // Save active=true first, then deactivate to satisfy query condition (active=false)
            Url recent = buildUrl(160L, "recent", "url", false, justExpired, user);
            Url old    = buildUrl(161L, "old",    "url2", false, longExpired, user);

            urlRepository.saveAllAndFlush(List.of(recent, old));

            Instant cutoff = NOW.minusSeconds(3600);
            List<String> codes = urlRepository.findRecentlyExpiredCodes(NOW, cutoff);

            assertThat(codes).containsExactly("recent");
            assertThat(codes).doesNotContain("old");
        }

        @Test
        @DisplayName("returns empty list when no urls expired in window")
        void returnsEmpty_whenNoneInWindow() {
            List<String> codes = urlRepository.findRecentlyExpiredCodes(NOW, PAST);

            assertThat(codes).isEmpty();
        }
    }


    // ── findAllShortCodes / findAllActiveShortCodes ──────────────────────────

    @Nested
    @DisplayName("findAllShortCodes and findAllActiveShortCodes")
    class FindAllShortCodes {

        @Test
        @DisplayName("findAllShortCodes returns both active and inactive codes")
        void returnsAll_includingInactive() {
            User user = createAndPersistUser();
            urlRepository.saveAllAndFlush(List.of(
                    buildUrl(170L, "active-code",   "url1", true,  FUTURE, user),
                    buildUrl(171L, "inactive-code",  "url2", false, FUTURE, user)
            ));

            List<String> codes = urlRepository.findAllShortCodes();

            assertThat(codes).containsExactlyInAnyOrder("active-code", "inactive-code");
        }

        @Test
        @DisplayName("findAllActiveShortCodes returns only active codes")
        void returnsOnlyActive() {
            User user = createAndPersistUser();
            urlRepository.saveAllAndFlush(List.of(
                    buildUrl(172L, "active-only",  "url1", true,  FUTURE, user),
                    buildUrl(173L, "should-hide",  "url2", false, FUTURE, user)
            ));

            List<String> codes = urlRepository.findAllActiveShortCodes();

            assertThat(codes).containsExactly("active-only");
            assertThat(codes).doesNotContain("should-hide");
        }

        @Test
        @DisplayName("findAllActiveShortCodes returns empty list when no active urls exist")
        void returnsEmpty_whenNoActiveUrls() {
            User user = createAndPersistUser();
            urlRepository.saveAndFlush(
                    buildUrl(174L, "gone", "url", false, FUTURE, user));

            List<String> codes = urlRepository.findAllActiveShortCodes();

            assertThat(codes).isEmpty();
        }
    }


    // ── Constraint Tests ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Constraint violations")
    class ConstraintTests {

        @Test
        @DisplayName("throws on FK violation when user is not persisted")
        void throws_whenUserNotPersisted() {
            User fakeUser = new User();
            fakeUser.setId(999L);

            assertThatThrownBy(() ->
                    urlRepository.saveAndFlush(
                            buildUrl(200L, "abc", "url", true, FUTURE, fakeUser))
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("throws when shortCode is null")
        void throws_whenShortCodeNull() {
            User user = createAndPersistUser();

            assertThatThrownBy(() ->
                    urlRepository.saveAndFlush(
                            buildUrl(201L, null, "url", true, FUTURE, user))
            ).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("throws on duplicate shortCode")
        void throws_onDuplicateShortCode() {
            User user = createAndPersistUser();
            urlRepository.saveAndFlush(buildUrl(202L, "dup", "url1", true, FUTURE, user));

            assertThatThrownBy(() ->
                    urlRepository.saveAndFlush(
                            buildUrl(203L, "dup", "url2", true, FUTURE, user))
            ).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}