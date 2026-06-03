package com.zubairmuwwakil.marketdata.repository;

import com.zubairmuwwakil.marketdata.model.entity.PipelineRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, Long> {

    Optional<PipelineRun> findTopByOrderByStartedAtDesc();

    Optional<PipelineRun> findByRunDate(LocalDate runDate);

    Optional<PipelineRun> findByIdempotencyKey(String idempotencyKey);
}