package com.zubairmuwwakil.marketdata.service.calendar;

import com.zubairmuwwakil.marketdata.config.MarketCalendarProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MarketCalendarService {

    private final Set<LocalDate> holidays;
    private final Set<LocalDate> earlyCloses;

    public MarketCalendarService(MarketCalendarProperties properties) {
        this.holidays = new HashSet<>(properties.getNyseHolidays());
        this.earlyCloses = new HashSet<>(properties.getNyseEarlyCloses());
    }

    public boolean isTradingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidays.contains(date);
    }

    public boolean isEarlyClose(LocalDate date) {
        return earlyCloses.contains(date);
    }

    @Cacheable(value = "marketCalendar", key = "#from.toString() + ':' + #to.toString()")
    public List<LocalDate> tradingDaysBetween(LocalDate from, LocalDate to) {
        return tradingDaysBetween(from, to, true);
    }

    @Cacheable(value = "marketCalendar", key = "#from.toString() + ':' + #to.toString() + ':' + #includeEarlyCloses")
    public List<LocalDate> tradingDaysBetween(LocalDate from, LocalDate to, boolean includeEarlyCloses) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            if (isTradingDay(cursor)) {
                if (!includeEarlyCloses && isEarlyClose(cursor)) {
                    cursor = cursor.plusDays(1);
                    continue;
                }
                days.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return days;
    }

    public List<LocalDate> earlyClosesBetween(LocalDate from, LocalDate to) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            if (isEarlyClose(cursor)) {
                days.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return days;
    }
}
