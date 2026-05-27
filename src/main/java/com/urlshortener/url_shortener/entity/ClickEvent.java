package com.urlshortener.url_shortener.entity;

import com.urlshortener.url_shortener.util.DeviceParser;
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

    @Column(nullable = false)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Column(columnDefinition = "TEXT")
    private String referrer;

    // "XX" = unknown/private IP. Nullable=false safe now since GeoIpService always returns a value.
    @Column(length = 2, nullable = false)
    private String country;

    // Parsed from userAgent at write time so analytics queries never load raw UA strings.
    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    @Builder.Default
    private DeviceParser.DeviceType deviceType = DeviceParser.DeviceType.DESKTOP;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant clickedAt;
}