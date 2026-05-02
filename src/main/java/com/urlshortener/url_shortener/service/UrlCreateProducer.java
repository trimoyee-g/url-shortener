package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.UrlCreateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlCreateProducer {

    private final KafkaTemplate<String, UrlCreateEvent> kafkaTemplate;
    private static final String TOPIC = "url-creations";

    public void publishCreate(UrlCreateEvent event) {
        kafkaTemplate.send(TOPIC, event.getShortCode(), event);
        log.debug("Published url create event for code: {}", event.getShortCode());
    }
}
