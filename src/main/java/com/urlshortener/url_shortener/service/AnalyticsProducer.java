package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.config.RabbitMQConfig;
import com.urlshortener.url_shortener.dto.UrlClickEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsProducer {

    private final RabbitTemplate rabbitTemplate;

    public void recordClick(UrlClickEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.CLICK_EXCHANGE,
                RabbitMQConfig.CLICK_ROUTING_KEY,
                event
        );
        log.debug("Published click event for code: {}", event.getShortCode());
    }
}
