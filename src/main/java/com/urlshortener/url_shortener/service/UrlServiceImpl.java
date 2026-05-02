package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.ShortenRequest;
import com.urlshortener.url_shortener.dto.UrlResponse;
import com.urlshortener.url_shortener.entity.Url;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.exception.AliasAlreadyExistsException;
import com.urlshortener.url_shortener.exception.UrlNotFoundException;
import com.urlshortener.url_shortener.repository.UrlRepository;
import com.urlshortener.url_shortener.repository.UserRepository;
import com.urlshortener.url_shortener.util.Base62Encoder;
import com.urlshortener.url_shortener.util.UrlBloomFilter;
import com.urlshortener.url_shortener.util.SnowflakeIdGenerator;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private final RedisTemplate<String, String> redisTemplate;
    private final Base62Encoder base62Encoder;
    private final SnowflakeIdGenerator idGenerator;
    private final UrlBloomFilter bloomFilter;
    private final UrlCreateProducer urlCreateProducer;
    private final MeterRegistry meterRegistry;
    private final RateLimiterService rateLimiterService;

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
                throw new AliasAlreadyExistsException(customAlias);
            }
        }

        long id = idGenerator.nextId();
        String shortCode = (customAlias != null && !customAlias.isBlank())
                ? customAlias
                : base62Encoder.encode(id);

        Instant expiresAt = computeExpiry(request.getTtlSeconds());

        Url url = Url.builder()
                .id(id)
                .shortCode(shortCode)
                .longUrl(normalizedUrl)
                .customAlias(customAlias)
                .user(user)
                .expiresAt(expiresAt)
                .active(true)
                .createdAt(Instant.now())
                .build();

        // Publish create event and return immediately to reduce latency.
        urlCreateProducer.publishCreate(com.urlshortener.url_shortener.dto.UrlCreateEvent.builder()
                .id(id)
                .shortCode(shortCode)
                .longUrl(normalizedUrl)
                .customAlias(customAlias)
                .userId(user.getId())
                .expiresAt(expiresAt)
                .createdAt(url.getCreatedAt())
                .build());

        log.info("Published short URL create event: {} -> {}", shortCode, url.getLongUrl());
        return toResponse(url);
    }

    // ── Resolve ──────────────────────────────────────────────────────────────

    @Override
    public String resolve(String shortCode) {
        Timer.Sample sample = Timer.start(meterRegistry);

        // 1. Cache lookup
        String cached = redisTemplate.opsForValue().get(cacheKey(shortCode));
        if (cached != null) {
            cacheHitCounter.increment();
            redirectSuccessCounter.increment();
            sample.stop(meterRegistry.timer("url.resolve.latency", "source", "cache"));
            return cached;
        }

        cacheMissCounter.increment();

        // 2. Bloom Filter Guard (Distributed Check)
        if (!bloomFilter.mightContain(shortCode)) {
            redirectFailedCounter.increment();
            throw new UrlNotFoundException(shortCode);
        }

        // 3. Database lookup
        Url url = urlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> {
                    redirectFailedCounter.increment();
                    return new UrlNotFoundException(shortCode);
                });

        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(Instant.now())) {
            redirectFailedCounter.increment();
            throw new UrlNotFoundException(shortCode);
        }

        // Repopulate cache
        cacheUrl(shortCode, url.getLongUrl(), url.getExpiresAt());

        redirectSuccessCounter.increment();
        sample.stop(meterRegistry.timer("url.resolve.latency", "source", "db"));

        return url.getLongUrl();
    }


    // ── Delete ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void delete(String shortCode, String userEmail) {
        Url url = urlRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (!url.getUser().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("You do not own this URL");
        }

        url.setActive(false);
        urlRepository.save(url);

        redisTemplate.delete(cacheKey(shortCode));
        log.info("Deleted short URL: {}", shortCode);
    }

    // ── List user's URLs ──────────────────────────────────────────────────────

    @Override
    public List<UrlResponse> getUserUrls(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return urlRepository.findByUserIdAndActiveTrueOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toResponse).toList();
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

    private UrlResponse toResponse(Url url) {
        return UrlResponse.builder()
                .shortUrl(baseUrl + "/" + url.getShortCode())
                .shortCode(url.getShortCode())
                .longUrl(url.getLongUrl())
                .customAlias(url.getCustomAlias())
                .expiresAt(url.getExpiresAt())
                .createdAt(url.getCreatedAt())
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