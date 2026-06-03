package com.zubairmuwwakil.marketdata.controller;

import com.zubairmuwwakil.marketdata.service.calendar.MarketCalendarService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarController {

    private final MarketCalendarService calendarService;

    public CalendarController(MarketCalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping("/nyse")
    public List<LocalDate> tradingDays(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(name = "excludeEarlyCloses", defaultValue = "false") boolean excludeEarlyCloses
    ) {
        return calendarService.tradingDaysBetween(from, to, !excludeEarlyCloses);
    }

    @GetMapping("/nyse/early-closes")
    public List<LocalDate> earlyCloses(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        return calendarService.earlyClosesBetween(from, to);
    }
}
