package com.urlshortener.url_shortener.service;

import com.urlshortener.url_shortener.dto.UrlClickEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AnalyticsProducerTests {

    @Mock
    private KafkaTemplate<String, UrlClickEvent> kafkaTemplate;

    @InjectMocks
    private AnalyticsProducer analyticsProducer;


    // ── Constants ────────────────────────────────────────────────────────────

    private static final String TOPIC = "url-clicks";


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
        @DisplayName("publishes click event to Kafka topic")
        void publishesClickEvent_toKafka() {

            // Arrange
            UrlClickEvent event = buildEvent("abc123");

            // Act
            analyticsProducer.recordClick(event);

            // Assert
            verify(kafkaTemplate).send(
                    TOPIC,
                    "abc123",
                    event
            );
        }

        @Test
        @DisplayName("uses shortCode as Kafka message key")
        void usesShortCode_asKafkaKey() {

            // Arrange
            UrlClickEvent event = buildEvent("partition-key");

            // Act
            analyticsProducer.recordClick(event);

            // Assert
            ArgumentCaptor<String> keyCaptor =
                    ArgumentCaptor.forClass(String.class);

            verify(kafkaTemplate).send(
                    eq(TOPIC),
                    keyCaptor.capture(),
                    eq(event)
            );

            assertThat(keyCaptor.getValue())
                    .isEqualTo("partition-key");
        }

        @Test
        @DisplayName("publishes exactly one Kafka message")
        void publishesExactlyOneMessage() {

            // Arrange
            UrlClickEvent event = buildEvent("single-send");

            // Act
            analyticsProducer.recordClick(event);

            // Assert
            verify(kafkaTemplate, times(1))
                    .send(anyString(), anyString(), any(UrlClickEvent.class));

            verifyNoMoreInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("passes the exact same event instance to Kafka")
        void passesSameEventInstance() {

            // Arrange
            UrlClickEvent event = buildEvent("same-instance");

            // Act
            analyticsProducer.recordClick(event);

            // Assert
            ArgumentCaptor<UrlClickEvent> eventCaptor =
                    ArgumentCaptor.forClass(UrlClickEvent.class);

            verify(kafkaTemplate).send(
                    eq(TOPIC),
                    eq("same-instance"),
                    eventCaptor.capture()
            );

            assertThat(eventCaptor.getValue())
                    .isSameAs(event);
        }

        @Test
        @DisplayName("supports publishing event with nullable optional fields")
        void supportsNullableFields() {

            // Arrange
            UrlClickEvent event = UrlClickEvent.builder()
                    .shortCode("minimal")
                    .build();

            // Act
            analyticsProducer.recordClick(event);

            // Assert
            verify(kafkaTemplate).send(
                    TOPIC,
                    "minimal",
                    event
            );
        }

        @Test
        @DisplayName("publishes multiple events independently")
        void publishesMultipleEventsIndependently() {

            // Arrange
            UrlClickEvent firstEvent = buildEvent("code-1");
            UrlClickEvent secondEvent = buildEvent("code-2");

            // Act
            analyticsProducer.recordClick(firstEvent);
            analyticsProducer.recordClick(secondEvent);

            // Assert
            verify(kafkaTemplate).send(TOPIC, "code-1", firstEvent);
            verify(kafkaTemplate).send(TOPIC, "code-2", secondEvent);

            verify(kafkaTemplate, times(2))
                    .send(anyString(), anyString(), any(UrlClickEvent.class));
        }
    }
}