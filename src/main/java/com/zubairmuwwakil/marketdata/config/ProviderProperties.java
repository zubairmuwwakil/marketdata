package com.zubairmuwwakil.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Which provider serves which path, and how hard we are willing to lean on each.
 *
 * <p>Routing is by <em>purpose</em>, not one global default (ADR 0003): the curated
 * watchlist keeps the sanctioned-but-tiny provider, while the dynamic per-user
 * quote path uses the one with headroom. Neither is real-time — both are daily
 * closes — so this is a sanction/quota split, not a latency split.
 */
@ConfigurationProperties(prefix = "marketdata.providers")
public class ProviderProperties {

    /** Provider serving curated watchlist ingestion, by source name. */
    private String ingestionEquity = "ALPHAVANTAGE";

    /** Provider serving the dynamic quote path, by source name. */
    private String quotesEquity = "YAHOO";

    /** Provider serving crypto ingestion, by source name. */
    private String ingestionCrypto = "BINANCE";

    /** Provider serving crypto dynamic quotes, by source name. */
    private String quotesCrypto = "BINANCE";

    private Yahoo yahoo = new Yahoo();
    private Binance binance = new Binance();
    private Questrade questrade = new Questrade();

    public String getIngestionEquity() { return ingestionEquity; }
    public void setIngestionEquity(String ingestionEquity) { this.ingestionEquity = ingestionEquity; }

    public String getQuotesEquity() { return quotesEquity; }
    public void setQuotesEquity(String quotesEquity) { this.quotesEquity = quotesEquity; }

    public String getIngestionCrypto() { return ingestionCrypto; }
    public void setIngestionCrypto(String ingestionCrypto) { this.ingestionCrypto = ingestionCrypto; }

    public String getQuotesCrypto() { return quotesCrypto; }
    public void setQuotesCrypto(String quotesCrypto) { this.quotesCrypto = quotesCrypto; }

    public Yahoo getYahoo() { return yahoo; }
    public void setYahoo(Yahoo yahoo) { this.yahoo = yahoo; }

    public Binance getBinance() { return binance; }
    public void setBinance(Binance binance) { this.binance = binance; }

    public Questrade getQuestrade() { return questrade; }
    public void setQuestrade(Questrade questrade) { this.questrade = questrade; }

    public static class Questrade {
        private boolean enabled = true;
        private String baseUrl = "https://api01.iq.questrade.com";
        private String accessToken = "";
        private int dailyBudget = 5000;
        private int maxSymbolsPerRequest = 50;
        private int maxConcurrency = 4;
        private Duration fanOutDeadline = Duration.ofSeconds(8);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

        public int getDailyBudget() { return dailyBudget; }
        public void setDailyBudget(int dailyBudget) { this.dailyBudget = dailyBudget; }

        public int getMaxSymbolsPerRequest() { return maxSymbolsPerRequest; }
        public void setMaxSymbolsPerRequest(int maxSymbolsPerRequest) { this.maxSymbolsPerRequest = maxSymbolsPerRequest; }

        public int getMaxConcurrency() { return maxConcurrency; }
        public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }

        public Duration getFanOutDeadline() { return fanOutDeadline; }
        public void setFanOutDeadline(Duration fanOutDeadline) { this.fanOutDeadline = fanOutDeadline; }
    }

    public static class Binance {
        private boolean enabled = true;
        private String baseUrl = "https://api.binance.com";
        private int dailyBudget = 10000;
        private int maxSymbolsPerRequest = 50;
        private int maxConcurrency = 4;
        private Duration fanOutDeadline = Duration.ofSeconds(8);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public int getDailyBudget() { return dailyBudget; }
        public void setDailyBudget(int dailyBudget) { this.dailyBudget = dailyBudget; }

        public int getMaxSymbolsPerRequest() { return maxSymbolsPerRequest; }
        public void setMaxSymbolsPerRequest(int maxSymbolsPerRequest) { this.maxSymbolsPerRequest = maxSymbolsPerRequest; }

        public int getMaxConcurrency() { return maxConcurrency; }
        public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }

        public Duration getFanOutDeadline() { return fanOutDeadline; }
        public void setFanOutDeadline(Duration fanOutDeadline) { this.fanOutDeadline = fanOutDeadline; }
    }

    public static class Yahoo {
        private boolean enabled = true;
        private String baseUrl = "https://query1.finance.yahoo.com";

        /**
         * Self-imposed politeness cap, NOT a vendor-published limit. Yahoo does not
         * sanction this access (E4), so we choose to be well-behaved rather than
         * discovering the real ceiling.
         */
        private int dailyBudget = 2000;

        /** Symbols accepted in one quote request. Yahoo has no working batch
         *  endpoint (v7 returns 401 as of 2026-08-18), so this bounds the fan-out. */
        private int maxSymbolsPerRequest = 50;

        private int maxConcurrency = 4;

        /** Total wall-clock budget for one request's fan-out. Anything unresolved
         *  when this expires falls back to cached last-known, labelled stale. */
        private Duration fanOutDeadline = Duration.ofSeconds(8);

        private String userAgent = "Mozilla/5.0 (compatible; MarketLens/1.0)";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public int getDailyBudget() { return dailyBudget; }
        public void setDailyBudget(int dailyBudget) { this.dailyBudget = dailyBudget; }

        public int getMaxSymbolsPerRequest() { return maxSymbolsPerRequest; }
        public void setMaxSymbolsPerRequest(int maxSymbolsPerRequest) { this.maxSymbolsPerRequest = maxSymbolsPerRequest; }

        public int getMaxConcurrency() { return maxConcurrency; }
        public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }

        public Duration getFanOutDeadline() { return fanOutDeadline; }
        public void setFanOutDeadline(Duration fanOutDeadline) { this.fanOutDeadline = fanOutDeadline; }

        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    }
}
