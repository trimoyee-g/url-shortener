package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.UrlClickEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsProducer {

    private final KafkaTemplate<String, UrlClickEvent> kafkaTemplate;
    private static final String TOPIC = "url-clicks";

    public void recordClick(UrlClickEvent event) {
        // Partitioning by shortCode ensures ordering for that specific URL
        kafkaTemplate.send(TOPIC, event.getShortCode(), event);
        log.debug("Published click event for code: {}", event.getShortCode());
    }
}