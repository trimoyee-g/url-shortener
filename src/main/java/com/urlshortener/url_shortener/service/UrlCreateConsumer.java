package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.UrlCreateEvent;
import com.urlshortener.url_shortener.entity.Url;
import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.repository.UrlRepository;
import com.urlshortener.url_shortener.repository.UserRepository;
import com.urlshortener.url_shortener.util.UrlBloomFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlCreateConsumer {

    private final UrlRepository urlRepository;
    private final UserRepository userRepository;
    private final UrlBloomFilter bloomFilter;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.cache.url-ttl-seconds:86400}")
    private long cacheTtlSeconds;

    @KafkaListener(topics = "url-creations", groupId = "url-create-group")
    public void handleCreateEvent(UrlCreateEvent evt) {
        try {
            // Resolve user
            User user = userRepository.findById(evt.getUserId()).orElse(null);

            Url url = Url.builder()
                    .id(evt.getId())
                    .shortCode(evt.getShortCode())
                    .longUrl(evt.getLongUrl())
                    .customAlias(evt.getCustomAlias())
                    .user(user)
                    .expiresAt(evt.getExpiresAt())
                    .active(true)
                    .createdAt(evt.getCreatedAt())
                    .build();

            urlRepository.save(url);

            // Add to bloom filter
            bloomFilter.add(evt.getShortCode());

            // Cache value
            long ttl = cacheTtlSeconds;
            if (evt.getExpiresAt() != null) {
                long secondsUntilExpiry = Duration.between(Instant.now(), evt.getExpiresAt()).getSeconds();
                ttl = Math.min(ttl, Math.max(1, secondsUntilExpiry));
            }
            redisTemplate.opsForValue().set("url:" + evt.getShortCode(), evt.getLongUrl(), Duration.ofSeconds(ttl));

            log.info("Consumed and persisted URL create for code: {}", evt.getShortCode());
        } catch (Exception e) {
            log.error("Error processing url create event for {}: {}", evt.getShortCode(), e.getMessage());
        }
    }
}
