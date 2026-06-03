package com.zubairmuwwakil.marketdata.controller;

import com.zubairmuwwakil.marketdata.model.entity.CorporateAction;
import com.zubairmuwwakil.marketdata.model.entity.CorporateActionType;
import com.zubairmuwwakil.marketdata.repository.CorporateActionRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/corporate-actions")
public class CorporateActionController {

    public record CorporateActionRequest(
            @NotNull String symbol,
            @NotNull LocalDate actionDate,
            @NotNull CorporateActionType actionType,
            BigDecimal splitFactor,
            BigDecimal dividend
    ) {}

    private final CorporateActionRepository repository;

    public CorporateActionController(CorporateActionRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<CorporateAction> list(@RequestParam String symbol) {
        return repository.findAllBySymbolOrderByActionDateAsc(symbol.toUpperCase(Locale.ROOT));
    }

    @PostMapping
    public ResponseEntity<CorporateAction> create(@RequestBody CorporateActionRequest request) {
        CorporateAction action = CorporateAction.builder()
                .symbol(request.symbol().toUpperCase(Locale.ROOT))
                .actionDate(request.actionDate())
                .actionType(request.actionType())
                .splitFactor(request.splitFactor())
                .dividend(request.dividend())
                .build();
        return ResponseEntity.ok(repository.save(action));
    }
}