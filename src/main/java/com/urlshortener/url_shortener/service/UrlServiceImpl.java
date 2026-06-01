package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.PagedResponse;
import com.urlshortener.url_shortener.dto.ShortenRequest;
import com.urlshortener.url_shortener.dto.UnlockRequest;
import com.urlshortener.url_shortener.dto.UnlockResponse;
import com.urlshortener.url_shortener.dto.UrlResponse;
import com.urlshortener.url_shortener.entity.Url;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.exception.AlreadyExistsException;
import com.urlshortener.url_shortener.exception.ForbiddenException;
import com.urlshortener.url_shortener.exception.PasswordRequiredException;
import com.urlshortener.url_shortener.exception.UrlNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.urlshortener.url_shortener.repository.ClickEventRepository;
import com.urlshortener.url_shortener.repository.UrlRepository;
import com.urlshortener.url_shortener.repository.UserRepository;
import com.urlshortener.url_shortener.util.Base62Encoder;
import com.urlshortener.url_shortener.util.UrlCuckooFilter;
import com.urlshortener.url_shortener.util.SnowflakeIdGenerator;
import com.urlshortener.url_shortener.util.UtmUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.ByteArrayOutputStream;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlServiceImpl implements UrlService {

    private final UrlRepository urlRepository;
    private final UserRepository userRepository;
    private final ClickEventRepository clickEventRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final Base62Encoder base62Encoder;
    private final SnowflakeIdGenerator idGenerator;
    private final UrlCuckooFilter cuckooFilter;
    private final MeterRegistry meterRegistry;
    private final RateLimiterService rateLimiterService;

    // BCrypt encoder — instantiated once; strength 12 is intentionally slow for password hashing.
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);

    // Sentinel cached for password-protected links so resolve() can detect protection on a
    // cache hit without a second Redis key or an extra DB round-trip.
    private static final String PROTECTED_SENTINEL = "__PROTECTED__";

    // Metrics
    private Counter redirectSuccessCounter;
    private Counter redirectFailedCounter;
    private Counter cacheHitCounter;
    private Counter cacheMissCounter;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.url.default-ttl-days:30}")
    private int defaultTtlDays;

    @Value("${app.cache.url-ttl-seconds:86400}")
    private long cacheTtlSeconds;

    @PostConstruct
    public void initMetrics() {
        this.redirectSuccessCounter = meterRegistry.counter("url.redirects", "status", "success");
        this.redirectFailedCounter = meterRegistry.counter("url.redirects", "status", "failed");
        this.cacheHitCounter = meterRegistry.counter("url.cache.hits");
        this.cacheMissCounter = meterRegistry.counter("url.cache.misses");
    }

    // ── Shorten ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UrlResponse shorten(ShortenRequest request, String userEmail, String remoteAddr) {
        // 1. Rate Limit Checks
        rateLimiterService.checkRateLimit("ip:" + remoteAddr);
        rateLimiterService.checkRateLimit("user:" + userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // 2. Normalize URL for Idempotency
        String normalizedUrl = request.getNormalizedUrl();

        return urlRepository.findByLongUrlAndUserIdAndActiveTrue(normalizedUrl, user.getId())
                .map(this::toResponse)
                .orElseGet(() -> createNewShortUrl(request, user, normalizedUrl));
    }

    private UrlResponse createNewShortUrl(ShortenRequest request, User user, String normalizedUrl) {
        String customAlias = request.getCustomAlias();
        if (customAlias != null && !customAlias.isBlank()) {
            if (urlRepository.existsByCustomAlias(customAlias)) {
                throw new AlreadyExistsException("Custom alias already exists: "+customAlias);
            }
        }

        long id = idGenerator.nextId();
        String shortCode = (customAlias != null && !customAlias.isBlank())
                ? customAlias
                : base62Encoder.encode(id);

        Instant expiresAt = computeExpiry(request.getTtlSeconds());

        // Append any requested UTM parameters to the destination URL
        String destinationUrl = UtmUtils.appendUtmParams(
                normalizedUrl,
                request.getUtmSource(),
                request.getUtmMedium(),
                request.getUtmCampaign());

        // Hash the password if one was provided
        String passwordHash = (request.getPassword() != null && !request.getPassword().isBlank())
                ? PASSWORD_ENCODER.encode(request.getPassword())
                : null;

        Url url = Url.builder()
                .id(id)
                .shortCode(shortCode)
                .longUrl(destinationUrl)
                .customAlias(customAlias)
                .user(user)
                .expiresAt(expiresAt)
                .passwordHash(passwordHash)
                .active(true)
                .createdAt(Instant.now())
                .build();

        urlRepository.save(url);
        // Cache the sentinel for protected links so resolve() never exposes the destination URL.
        // For open links, cache the UTM-enriched destination URL (not the bare normalizedUrl).
        cacheUrl(shortCode, passwordHash != null ? PROTECTED_SENTINEL : destinationUrl, expiresAt);
        cuckooFilter.add(shortCode);

        log.info("Created short URL: {} -> {}", shortCode, url.getLongUrl());
        return toResponse(url);
    }

    // ── Resolve ──────────────────────────────────────────────────────────────

    @Override
    public String resolve(String shortCode) {
        Timer.Sample sample = Timer.start(meterRegistry);

        // 1. Cuckoo Filter guard — non-existent codes are rejected in microseconds,
        //    before Redis or MySQL are ever touched
        if (!cuckooFilter.mightContain(shortCode)) {
            redirectFailedCounter.increment();
            throw new UrlNotFoundException(shortCode);
        }

        // 2. Cache lookup — only reached when code might exist
        String cached = redisTemplate.opsForValue().get(cacheKey(shortCode));
        if (cached != null) {
            cacheHitCounter.increment();
            sample.stop(meterRegistry.timer("url.resolve.latency", "source", "cache"));
            if (PROTECTED_SENTINEL.equals(cached)) {
                throw new PasswordRequiredException(shortCode);
            }
            redirectSuccessCounter.increment();
            return cached;
        }

        cacheMissCounter.increment();

        // 3. Database lookup
        Url url = urlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> {
                    redirectFailedCounter.increment();
                    return new UrlNotFoundException(shortCode);
                });

        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(Instant.now())) {
            cuckooFilter.delete(shortCode);
            redirectFailedCounter.increment();
            throw new UrlNotFoundException(shortCode);
        }

        boolean isProtected = url.getPasswordHash() != null;
        // Repopulate cache — store sentinel for protected links so future cache hits
        // also route to the unlock page without exposing the destination URL.
        cacheUrl(shortCode, isProtected ? PROTECTED_SENTINEL : url.getLongUrl(), url.getExpiresAt());

        sample.stop(meterRegistry.timer("url.resolve.latency", "source", "db"));

        if (isProtected) {
            throw new PasswordRequiredException(shortCode);
        }

        redirectSuccessCounter.increment();
        return url.getLongUrl();
    }


    // ── Delete ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void delete(String shortCode, String userEmail) {
        Url url = urlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (!url.getUser().getEmail().equals(userEmail)) {
            throw new ForbiddenException("You do not own this URL");
        }

        url.setActive(false);
        urlRepository.save(url);

        redisTemplate.delete(cacheKey(shortCode));
        cuckooFilter.delete(shortCode);
        log.info("Deleted short URL: {}", shortCode);
    }

    // ── List user's URLs ──────────────────────────────────────────────────────

    private static final int PAGE_SIZE = 10;

    @Override
    public PagedResponse<UrlResponse> getUserUrls(String userEmail, int page) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Page<Url> urlPage = urlRepository.findByUserIdAndActiveTrueOrderByCreatedAtDesc(
                user.getId(), PageRequest.of(page, PAGE_SIZE));

        if (urlPage.isEmpty()) return PagedResponse.<UrlResponse>builder()
                .content(List.of()).page(page).pageSize(PAGE_SIZE)
                .totalElements(0).totalPages(0).last(true).build();

        // Batch-load click counts in a single query to avoid N+1
        List<String> codes = urlPage.stream().map(Url::getShortCode).toList();
        Map<String, Long> clickCounts = batchClickCounts(codes);

        List<UrlResponse> content = urlPage.stream()
                .map(url -> toResponse(url, clickCounts.getOrDefault(url.getShortCode(), 0L)))
                .toList();

        return PagedResponse.<UrlResponse>builder()
                .content(content)
                .page(page)
                .pageSize(PAGE_SIZE)
                .totalElements(urlPage.getTotalElements())
                .totalPages(urlPage.getTotalPages())
                .last(urlPage.isLast())
                .build();
    }

    /**
     * Fetches click counts for a list of short codes in one DB round-trip,
     * merging with Redis values where available (Redis wins — it's more current).
     */
    private Map<String, Long> batchClickCounts(List<String> codes) {
        // Start from DB aggregates
        Map<String, Long> counts = clickEventRepository.countClicksPerCode(codes).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]));

        // Overwrite with Redis values where present
        codes.forEach(code -> {
            String cached = redisTemplate.opsForValue().get("clicks:" + code);
            if (cached != null) counts.put(code, Long.parseLong(cached));
        });

        return counts;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void cacheUrl(String shortCode, String longUrl, Instant expiresAt) {
        long ttl = cacheTtlSeconds;
        if (expiresAt != null) {
            long secondsUntilExpiry = Duration.between(Instant.now(), expiresAt).getSeconds();
            ttl = Math.min(ttl, Math.max(1, secondsUntilExpiry));
        }
        redisTemplate.opsForValue().set(cacheKey(shortCode), longUrl, Duration.ofSeconds(ttl));
    }

    private Instant computeExpiry(Long ttlSeconds) {
        if (ttlSeconds != null && ttlSeconds > 0) {
            return Instant.now().plusSeconds(ttlSeconds);
        }
        return Instant.now().plus(Duration.ofDays(defaultTtlDays));
    }

    private String cacheKey(String code) {
        return "url:" + code;
    }

    /** Used when click count is already known (e.g. batch-loaded in getUserUrls). */
    private UrlResponse toResponse(Url url, long clickCount) {
        UrlResponse.LinkStatus status = (url.getExpiresAt() != null
                && url.getExpiresAt().isBefore(Instant.now()))
                ? UrlResponse.LinkStatus.EXPIRED
                : UrlResponse.LinkStatus.ACTIVE;

        return UrlResponse.builder()
                .shortUrl(baseUrl + "/" + url.getShortCode())
                .shortCode(url.getShortCode())
                .longUrl(url.getLongUrl())
                .customAlias(url.getCustomAlias())
                .expiresAt(url.getExpiresAt())
                .createdAt(url.getCreatedAt())
                .clickCount(clickCount)
                .status(status)
                .build();
    }

    /** Used for single-URL responses (shorten, QR) where we look up the click count individually. */
    private UrlResponse toResponse(Url url) {
        String cached = redisTemplate.opsForValue().get("clicks:" + url.getShortCode());
        long clickCount = cached != null
                ? Long.parseLong(cached)
                : clickEventRepository.countByShortCode(url.getShortCode());
        return toResponse(url, clickCount);
    }

    // ── Unlock (password-protected links) ────────────────────────────────────

    @Override
    public UnlockResponse unlock(String shortCode, UnlockRequest request) {
        Url url = urlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (url.getPasswordHash() == null) {
            // Link has no password — return the redirect URL directly
            return UnlockResponse.builder()
                    .shortCode(shortCode)
                    .redirectUrl(url.getLongUrl())
                    .build();
        }

        if (!PASSWORD_ENCODER.matches(request.getPassword(), url.getPasswordHash())) {
            throw new BadCredentialsException("Incorrect password");
        }

        return UnlockResponse.builder()
                .shortCode(shortCode)
                .redirectUrl(url.getLongUrl())
                .build();
    }

    @Override
    public byte[] generateQr(String shortCode, int size) {
        Url url = urlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        String shortUrl = baseUrl + "/" + url.getShortCode();

        QRCodeWriter qrWriter = new QRCodeWriter();
        try {
            BitMatrix matrix = qrWriter.encode(shortUrl, BarcodeFormat.QR_CODE, size, size);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
            return baos.toByteArray();
        } catch (WriterException | IOException e) {
            log.error("Failed to generate QR for {}", shortCode, e);
            throw new RuntimeException("Failed to generate QR code");
        }
    }
}