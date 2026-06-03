package com.zubairmuwwakil.marketdata.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "api_quota_usage",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "usage_date"})
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ApiQuotaUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "calls_used", nullable = false)
    private int callsUsed;

    @Column(name = "calls_limit", nullable = false)
    private int callsLimit;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}