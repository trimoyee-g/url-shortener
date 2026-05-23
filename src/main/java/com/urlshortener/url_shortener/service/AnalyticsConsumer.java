package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.UrlClickEvent;
import com.urlshortener.url_shortener.entity.ClickEvent;
import com.urlshortener.url_shortener.repository.ClickEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsConsumer {

    private final ClickEventRepository clickEventRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final GeoIpService geoIpService;

    @KafkaListener(topics = "url-clicks", groupId = "analytics-group")
    public void handleClickEvent(UrlClickEvent dto) {
        try {
            String ip = dto.getIpAddress();
            String country = geoIpService.getCountryCode(ip != null ? ip : "");

            ClickEvent event = ClickEvent.builder()
                    .shortCode(dto.getShortCode())
                    .ipAddress(ip)
                    .userAgent(dto.getUserAgent())
                    .referrer(dto.getReferer())
                    .country(country)
                    .build();

            clickEventRepository.save(event);
            redisTemplate.opsForValue().increment("clicks:" + dto.getShortCode());

            log.debug("Processed click event for: {}", dto.getShortCode());

        } catch (Exception e) {
            log.error("Error processing click event for {}: {}", dto.getShortCode(), e.getMessage(), e);
        }
    }
}