package com.zubairmuwwakil.marketdata.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "corporate_action",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "action_date", "action_type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorporateAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(name = "action_date", nullable = false)
    private LocalDate actionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private CorporateActionType actionType;

    @Column(name = "split_factor", precision = 10, scale = 6)
    private BigDecimal splitFactor;

    @Column(name = "dividend", precision = 10, scale = 6)
    private BigDecimal dividend;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}