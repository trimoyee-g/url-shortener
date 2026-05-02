package com.urlshortener.url_shortener.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "click_events", indexes = {
        @Index(name = "idx_clicks_short_code", columnList = "shortCode"),
        @Index(name = "idx_clicks_code_time",  columnList = "shortCode, clickedAt")
})
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String shortCode;

    @Column(length = 45)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Column(columnDefinition = "TEXT")
    private String referrer;

    @Column(length = 2)
    private String country;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant clickedAt;
}
