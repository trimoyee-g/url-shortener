package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.UrlCreateEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UrlCreateProducerTests {

    @Mock
    private KafkaTemplate<String, UrlCreateEvent> kafkaTemplate;

    @InjectMocks
    private UrlCreateProducer urlCreateProducer;

    private static final String TOPIC = "url-creations";

    private UrlCreateEvent buildEvent() {
        return UrlCreateEvent.builder()
                .id(1L)
                .shortCode("abc123")
                .longUrl("https://google.com")
                .customAlias("google")
                .userId(10L)
                .expiresAt(Instant.parse("2026-01-02T00:00:00Z"))
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    @Nested
    @DisplayName("publishCreate")
    class PublishCreate {

        @Test
        @DisplayName("publishes url create event to Kafka")
        void publishesEvent_toKafka() {

            // Arrange
            UrlCreateEvent event = buildEvent();

            // Act
            urlCreateProducer.publishCreate(event);

            // Assert
            verify(kafkaTemplate)
                    .send(TOPIC, event.getShortCode(), event);
        }

        @Test
        @DisplayName("uses shortCode as Kafka partition key")
        void usesShortCode_asPartitionKey() {

            // Arrange
            UrlCreateEvent event = buildEvent();

            // Act
            urlCreateProducer.publishCreate(event);

            // Assert
            verify(kafkaTemplate)
                    .send(
                            TOPIC,
                            "abc123",
                            event
                    );
        }

        @Test
        @DisplayName("publishes event with all metadata intact")
        void publishesEvent_withCompletePayload() {

            // Arrange
            UrlCreateEvent event = buildEvent();

            // Act
            urlCreateProducer.publishCreate(event);

            // Assert
            verify(kafkaTemplate)
                    .send(
                            TOPIC,
                            "abc123",
                            event
                    );
        }
    }
}