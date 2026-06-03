package com.zubairmuwwakil.marketdata.controller;

import com.zubairmuwwakil.marketdata.model.dto.IngestionQuarantineEntry;
import com.zubairmuwwakil.marketdata.repository.IngestionQuarantineRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ingestion")
public class IngestionQuarantineController {

    private final IngestionQuarantineRepository repository;

    public IngestionQuarantineController(IngestionQuarantineRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/quarantine")
    public List<IngestionQuarantineEntry> list(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "200") int limit
    ) {
        return repository.list(symbol, from, to, limit);
    }
}
