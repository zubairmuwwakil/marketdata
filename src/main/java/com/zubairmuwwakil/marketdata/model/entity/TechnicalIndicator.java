package com.zubairmuwwakil.marketdata.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "technical_indicator",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"symbol", "trade_date", "indicator_type"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicalIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_type", nullable = false, length = 32)
    private IndicatorType indicatorType;

    @Column(name = "\"value\"", nullable = false, precision = 18, scale = 8)
    private BigDecimal value;
}
