package com.zubairmuwwakil.marketdata.controller;

import com.zubairmuwwakil.marketdata.service.quality.DataQualityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/quality")
public class DataQualityController {

    private final DataQualityService qualityService;

    public DataQualityController(DataQualityService qualityService) {
        this.qualityService = qualityService;
    }

    @GetMapping("/report")
    public DataQualityService.QualityReport report(
            @RequestParam String symbol,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        return qualityService.report(symbol.toUpperCase(Locale.ROOT), from, to);
    }
}