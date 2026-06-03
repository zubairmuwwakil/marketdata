package com.zubairmuwwakil.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "marketdata.calendar")
public class MarketCalendarProperties {

    private List<LocalDate> nyseHolidays = new ArrayList<>();
    private List<LocalDate> nyseEarlyCloses = new ArrayList<>();

    public List<LocalDate> getNyseHolidays() {
        return nyseHolidays;
    }

    public void setNyseHolidays(List<LocalDate> nyseHolidays) {
        this.nyseHolidays = nyseHolidays;
    }

    public List<LocalDate> getNyseEarlyCloses() {
        return nyseEarlyCloses;
    }

    public void setNyseEarlyCloses(List<LocalDate> nyseEarlyCloses) {
        this.nyseEarlyCloses = nyseEarlyCloses;
    }
}
