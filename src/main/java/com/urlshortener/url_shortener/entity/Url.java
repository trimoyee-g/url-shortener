package com.urlshortener.url_shortener.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "urls", indexes = {
        @Index(name = "idx_urls_short_code", columnList = "shortCode", unique = true),
        @Index(name = "idx_urls_user_id",    columnList = "user_id"),
        @Index(name = "idx_urls_expires_at", columnList = "expiresAt")
})
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Url {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String shortCode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String longUrl;

    @Column(length = 50)
    private String customAlias;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    @PreUpdate
    public void normalize() {
        if (shortCode != null) shortCode = shortCode.trim();
        if (longUrl != null) longUrl = longUrl.trim();
        if (customAlias != null) customAlias = customAlias.trim();
    }
}
