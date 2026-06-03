package com.zubairmuwwakil.marketdata.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "pipeline_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PipelineStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 20)
    private PipelineRunType runType;

    @Column(name = "symbols_expected", nullable = false)
    private Integer symbolsExpected;

    @Column(name = "symbols_processed", nullable = false)
    private Integer symbolsProcessed;

    @Column(name = "symbols_failed", nullable = false)
    private Integer symbolsFailed;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "requested_from")
    private LocalDate requestedFrom;

    @Column(name = "requested_to")
    private LocalDate requestedTo;

    @Column(name = "idempotency_key", length = 64, unique = true)
    private String idempotencyKey;

    @Column(name = "started_by", length = 60)
    private String startedBy;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @PrePersist
    void prePersist() {
        if (startedAt == null) startedAt = Instant.now();
        if (runType == null) runType = PipelineRunType.DAILY;
        if (status == null) status = PipelineStatus.RUNNING;
        if (symbolsExpected == null) symbolsExpected = 0;
        if (symbolsProcessed == null) symbolsProcessed = 0;
        if (symbolsFailed == null) symbolsFailed = 0;
        if (retryCount == null) retryCount = 0;
    }
}