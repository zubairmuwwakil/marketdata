package com.zubairmuwwakil.marketdata.controller;

import com.zubairmuwwakil.marketdata.model.entity.PipelineRun;
import com.zubairmuwwakil.marketdata.repository.PipelineRunRepository;
import com.zubairmuwwakil.marketdata.service.ingestion.IngestionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ingestion")
public class IngestionRunController {

    public record BackfillRequest(List<String> symbols, LocalDate from, LocalDate to) {}

    private final IngestionService ingestionService;
    private final PipelineRunRepository runRepository;

    public IngestionRunController(IngestionService ingestionService, PipelineRunRepository runRepository) {
        this.ingestionService = ingestionService;
        this.runRepository = runRepository;
    }

    @PostMapping("/run")
    public ResponseEntity<PipelineRun> runDaily(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ingestionService.ingestDaily(idempotencyKey, startedBy(authentication)));
    }

    @PostMapping("/backfill")
    public ResponseEntity<PipelineRun> backfill(
            @RequestBody BackfillRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        if (request == null || request.from() == null || request.to() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(
            ingestionService.ingestBackfill(request.symbols(), request.from(), request.to(), idempotencyKey, startedBy(authentication))
        );
    }

    @GetMapping("/runs")
    public List<PipelineRun> runs(@RequestParam(defaultValue = "20") int limit) {
        int bounded = Math.min(Math.max(limit, 1), 100);
        return runRepository.findAll(PageRequest.of(0, bounded, Sort.by("startedAt").descending())).getContent();
    }

    @GetMapping("/runs/latest")
    public ResponseEntity<PipelineRun> latest() {
        return runRepository.findTopByOrderByStartedAtDesc()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<PipelineRun> get(@PathVariable Long id) {
        return runRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/runs/{id}/retry")
    public ResponseEntity<PipelineRun> retry(@PathVariable Long id, Authentication authentication) {
        var opt = runRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PipelineRun run = opt.get();
        if (run.getRunType() == com.zubairmuwwakil.marketdata.model.entity.PipelineRunType.BACKFILL) {
            if (run.getRequestedFrom() == null || run.getRequestedTo() == null) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(
                    ingestionService.ingestBackfill(null, run.getRequestedFrom(), run.getRequestedTo(), null, startedBy(authentication))
            );
        }
        return ResponseEntity.ok(ingestionService.ingestDaily(null, startedBy(authentication)));
    }

    private String startedBy(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return "api";
        }
        return authentication.getName();
    }
}