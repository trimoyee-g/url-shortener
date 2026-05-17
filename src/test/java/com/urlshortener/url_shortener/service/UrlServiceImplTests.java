package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.ShortenRequest;
import com.urlshortener.url_shortener.dto.UrlResponse;
import com.urlshortener.url_shortener.entity.Url;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.exception.AlreadyExistsException;
import com.urlshortener.url_shortener.exception.ForbiddenException;
import com.urlshortener.url_shortener.exception.UrlNotFoundException;
import com.urlshortener.url_shortener.repository.UrlRepository;
import com.urlshortener.url_shortener.repository.UserRepository;
import com.urlshortener.url_shortener.util.Base62Encoder;
import com.urlshortener.url_shortener.util.SnowflakeIdGenerator;
import com.urlshortener.url_shortener.util.UrlBloomFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceImplTests {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private Base62Encoder base62Encoder;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private UrlBloomFilter bloomFilter;

    @Mock
    private UrlCreateProducer urlCreateProducer;

    @Mock
    private RateLimiterService rateLimiterService;

    private MeterRegistry meterRegistry;

    private UrlServiceImpl urlService;

    @BeforeEach
    void setUp() {

        meterRegistry = new SimpleMeterRegistry();

        urlService = new UrlServiceImpl(
                urlRepository,
                userRepository,
                redisTemplate,
                base62Encoder,
                idGenerator,
                bloomFilter,
                urlCreateProducer,
                meterRegistry,
                rateLimiterService
        );

        ReflectionTestUtils.setField(
                urlService,
                "baseUrl",
                "http://localhost:8080"
        );

        ReflectionTestUtils.setField(
                urlService,
                "defaultTtlDays",
                30
        );

        ReflectionTestUtils.setField(
                urlService,
                "cacheTtlSeconds",
                86400L
        );

        urlService.initMetrics();

        lenient().when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);
    }

    @Nested
    class Shorten {

        @Test
        @DisplayName("returns existing URL if already shortened")
        void returnsExistingUrl() {

            String email = "test@example.com";
            String ip = "127.0.0.1";

            User user = buildUser();

            ShortenRequest request = new ShortenRequest();
            request.setLongUrl("https://google.com");

            Url existing = buildUrl("abc123");

            when(userRepository.findByEmail(email))
                    .thenReturn(Optional.of(user));

            when(urlRepository.findByLongUrlAndUserIdAndActiveTrue(
                    anyString(),
                    eq(user.getId())
            )).thenReturn(Optional.of(existing));

            UrlResponse response =
                    urlService.shorten(request, email, ip);

            assertThat(response.getShortCode())
                    .isEqualTo("abc123");

            verify(urlCreateProducer, never())
                    .publishCreate(any());
        }

        @Test
        @DisplayName("creates new short URL")
        void createsNewShortUrl() {

            String email = "test@example.com";
            String ip = "127.0.0.1";

            User user = buildUser();

            ShortenRequest request = new ShortenRequest();
            request.setLongUrl("https://google.com");

            when(userRepository.findByEmail(email))
                    .thenReturn(Optional.of(user));

            when(urlRepository.findByLongUrlAndUserIdAndActiveTrue(
                    anyString(),
                    eq(user.getId())
            )).thenReturn(Optional.empty());

            when(idGenerator.nextId())
                    .thenReturn(123L);

            when(base62Encoder.encode(123L))
                    .thenReturn("abc123");

            UrlResponse response =
                    urlService.shorten(request, email, ip);

            assertThat(response.getShortCode())
                    .isEqualTo("abc123");

            verify(urlCreateProducer)
                    .publishCreate(any());
        }

        @Test
        @DisplayName("throws when custom alias already exists")
        void throwsWhenAliasExists() {

            String email = "test@example.com";
            String ip = "127.0.0.1";

            User user = buildUser();

            ShortenRequest request = new ShortenRequest();
            request.setLongUrl("https://google.com");
            request.setCustomAlias("myalias");

            when(userRepository.findByEmail(email))
                    .thenReturn(Optional.of(user));

            when(urlRepository.findByLongUrlAndUserIdAndActiveTrue(
                    anyString(),
                    eq(user.getId())
            )).thenReturn(Optional.empty());

            when(urlRepository.existsByCustomAlias("myalias"))
                    .thenReturn(true);

            assertThatThrownBy(() ->
                    urlService.shorten(request, email, ip)
            ).isInstanceOf(AlreadyExistsException.class);
        }

        @Test
        @DisplayName("throws when user not found")
        void throwsWhenUserNotFound() {

            ShortenRequest request = new ShortenRequest();
            request.setLongUrl("https://google.com");

            when(userRepository.findByEmail(anyString()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    urlService.shorten(
                            request,
                            "missing@example.com",
                            "127.0.0.1"
                    )
            ).isInstanceOf(UsernameNotFoundException.class);
        }
    }

    @Nested
    class Resolve {

        @Test
        @DisplayName("resolves URL from cache")
        void resolvesFromCache() {

            String shortCode = "abc123";

            when(bloomFilter.mightContain(shortCode))
                    .thenReturn(true);

            when(valueOperations.get("url:" + shortCode))
                    .thenReturn("https://google.com");

            String result = urlService.resolve(shortCode);

            assertThat(result)
                    .isEqualTo("https://google.com");

            verify(urlRepository, never())
                    .findByShortCodeAndActiveTrue(anyString());
        }

        @Test
        @DisplayName("resolves URL from DB when cache miss occurs")
        void resolvesFromDb() {

            String shortCode = "abc123";

            Url url = Url.builder()
                    .shortCode(shortCode)
                    .longUrl("https://google.com")
                    .active(true)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            when(valueOperations.get("url:" + shortCode))
                    .thenReturn(null);

            when(bloomFilter.mightContain(shortCode))
                    .thenReturn(true);

            when(urlRepository.findByShortCodeAndActiveTrue(shortCode))
                    .thenReturn(Optional.of(url));

            String result = urlService.resolve(shortCode);

            assertThat(result)
                    .isEqualTo("https://google.com");

            verify(urlRepository)
                    .findByShortCodeAndActiveTrue(shortCode);

            verify(valueOperations)
                    .set(
                            eq("url:" + shortCode),
                            eq("https://google.com"),
                            any(Duration.class)
                    );
        }

        @Test
        @DisplayName("throws when bloom filter rejects code — Redis is never consulted")
        void throwsWhenBloomFilterRejects() {

            String shortCode = "missing";

            when(bloomFilter.mightContain(shortCode))
                    .thenReturn(false);

            assertThatThrownBy(() ->
                    urlService.resolve(shortCode)
            ).isInstanceOf(UrlNotFoundException.class);

            verify(valueOperations, never()).get(anyString());
        }

        @Test
        @DisplayName("throws when URL expired")
        void throwsWhenUrlExpired() {

            String shortCode = "expired";

            Url url = Url.builder()
                    .shortCode(shortCode)
                    .longUrl("https://google.com")
                    .active(true)
                    .expiresAt(Instant.now().minusSeconds(60))
                    .build();

            when(valueOperations.get("url:" + shortCode))
                    .thenReturn(null);

            when(bloomFilter.mightContain(shortCode))
                    .thenReturn(true);

            when(urlRepository.findByShortCodeAndActiveTrue(shortCode))
                    .thenReturn(Optional.of(url));

            assertThatThrownBy(() ->
                    urlService.resolve(shortCode)
            ).isInstanceOf(UrlNotFoundException.class);
        }
    }

    @Nested
    class Delete {

        @Test
        @DisplayName("deletes URL successfully")
        void deletesSuccessfully() {

            User user = buildUser();

            Url url = buildUrl("abc123");
            url.setUser(user);

            when(urlRepository.findByShortCodeAndActiveTrue("abc123"))
                    .thenReturn(Optional.of(url));

            urlService.delete("abc123", user.getEmail());

            assertThat(url.isActive())
                    .isFalse();

            verify(urlRepository).save(url);

            verify(redisTemplate)
                    .delete("url:abc123");
        }

        @Test
        @DisplayName("throws when deleting another user's URL")
        void throwsWhenWrongOwner() {

            User owner = buildUser();

            Url url = buildUrl("abc123");
            url.setUser(owner);

            when(urlRepository.findByShortCodeAndActiveTrue("abc123"))
                    .thenReturn(Optional.of(url));

            assertThatThrownBy(() ->
                    urlService.delete(
                            "abc123",
                            "other@example.com"
                    )
            ).isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    class GetUserUrls {

        @Test
        @DisplayName("returns user URLs")
        void returnsUserUrls() {

            User user = buildUser();

            when(userRepository.findByEmail(user.getEmail()))
                    .thenReturn(Optional.of(user));

            when(urlRepository.findByUserIdAndActiveTrueOrderByCreatedAtDesc(
                    user.getId()
            )).thenReturn(Set.of(
                    buildUrl("a1"),
                    buildUrl("a2")
            ));

            List<UrlResponse> responses =
                    urlService.getUserUrls(user.getEmail());

            assertThat(responses)
                    .hasSize(2);
        }
    }

    @Nested
    class GenerateQr {

        @Test
        @DisplayName("generates QR code")
        void generatesQr() {

            Url url = buildUrl("abc123");

            when(urlRepository.findByShortCodeAndActiveTrue("abc123"))
                    .thenReturn(Optional.of(url));

            byte[] qr = urlService.generateQr("abc123", 300);

            assertThat(qr)
                    .isNotNull();

            assertThat(qr.length)
                    .isGreaterThan(0);
        }

        @Test
        @DisplayName("throws when QR URL not found")
        void throwsWhenQrUrlMissing() {

            when(urlRepository.findByShortCodeAndActiveTrue("abc123"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    urlService.generateQr("abc123", 300)
            ).isInstanceOf(UrlNotFoundException.class);
        }
    }

    private User buildUser() {

        return User.builder()
                .id(1L)
                .email("test@example.com")
                .password("password")
                .name("Test User")
                .build();
    }

    private Url buildUrl(String code) {

        return Url.builder()
                .id(1L)
                .shortCode(code)
                .longUrl("https://google.com")
                .customAlias(null)
                .active(true)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}