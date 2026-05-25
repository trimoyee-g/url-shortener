package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.config.RabbitMQConfig;
import com.urlshortener.url_shortener.dto.UrlClickEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AnalyticsProducerTests {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AnalyticsProducer analyticsProducer;


    // ── Helpers ──────────────────────────────────────────────────────────────

    private UrlClickEvent buildEvent(String shortCode) {
        return UrlClickEvent.builder()
                .shortCode(shortCode)
                .longUrl("https://example.com")
                .timestamp("2026-01-01T00:00:00Z")
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .referer("https://google.com")
                .build();
    }


    // ── recordClick ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordClick")
    class RecordClick {

        @Test
        @DisplayName("publishes click event to RabbitMQ exchange")
        void publishesClickEvent_toRabbitMQ() {
            UrlClickEvent event = buildEvent("abc123");

            analyticsProducer.recordClick(event);

            verify(rabbitTemplate).convertAndSend(
                    RabbitMQConfig.CLICK_EXCHANGE,
                    RabbitMQConfig.CLICK_ROUTING_KEY,
                    event
            );
        }

        @Test
        @DisplayName("publishes exactly one message")
        void publishesExactlyOneMessage() {
            UrlClickEvent event = buildEvent("single-send");

            analyticsProducer.recordClick(event);

            verify(rabbitTemplate, times(1))
                    .convertAndSend(anyString(), anyString(), any(UrlClickEvent.class));

            verifyNoMoreInteractions(rabbitTemplate);
        }

        @Test
        @DisplayName("publishes multiple events independently")
        void publishesMultipleEventsIndependently() {
            UrlClickEvent first = buildEvent("code-1");
            UrlClickEvent second = buildEvent("code-2");

            analyticsProducer.recordClick(first);
            analyticsProducer.recordClick(second);

            verify(rabbitTemplate).convertAndSend(
                    RabbitMQConfig.CLICK_EXCHANGE, RabbitMQConfig.CLICK_ROUTING_KEY, first);
            verify(rabbitTemplate).convertAndSend(
                    RabbitMQConfig.CLICK_EXCHANGE, RabbitMQConfig.CLICK_ROUTING_KEY, second);

            verify(rabbitTemplate, times(2))
                    .convertAndSend(anyString(), anyString(), any(UrlClickEvent.class));
        }

        @Test
        @DisplayName("supports publishing event with nullable optional fields")
        void supportsNullableFields() {
            UrlClickEvent event = UrlClickEvent.builder()
                    .shortCode("minimal")
                    .build();

            analyticsProducer.recordClick(event);

            verify(rabbitTemplate).convertAndSend(
                    RabbitMQConfig.CLICK_EXCHANGE,
                    RabbitMQConfig.CLICK_ROUTING_KEY,
                    event
            );
        }
    }
}
