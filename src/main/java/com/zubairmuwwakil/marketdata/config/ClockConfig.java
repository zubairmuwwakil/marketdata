package com.zubairmuwwakil.marketdata.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * A single injectable clock.
 *
 * <p>The quote path's whole honesty story turns on one boundary — whether the
 * most recent session has closed yet — and a service reading the wall clock
 * directly cannot be tested at that boundary. Labelling this morning's data
 * "stale" because today's close has not happened yet would train consumers to
 * ignore the staleness flag, which is worse than not having one.
 */
@Configuration
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
