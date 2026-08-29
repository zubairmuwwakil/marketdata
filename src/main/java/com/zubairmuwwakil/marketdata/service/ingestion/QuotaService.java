package com.zubairmuwwakil.marketdata.service.ingestion;

import com.zubairmuwwakil.marketdata.model.entity.ApiQuotaUsage;
import com.zubairmuwwakil.marketdata.repository.ApiQuotaUsageRepository;
import com.zubairmuwwakil.marketdata.repository.DatabaseDialect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class QuotaService {

    public static final String PROVIDER = "ALPHAVANTAGE";
    public static final int DAILY_LIMIT = 25;

    private final ApiQuotaUsageRepository repo;
    private final JdbcTemplate jdbcTemplate;
    private final boolean h2Database;

    public QuotaService(ApiQuotaUsageRepository repo, JdbcTemplate jdbcTemplate) {
        this.repo = repo;
        this.jdbcTemplate = jdbcTemplate;
        this.h2Database = DatabaseDialect.isH2(jdbcTemplate.getDataSource());
    }

    @Transactional
    public ApiQuotaUsage getOrCreateToday() {
        return getOrCreateToday(PROVIDER, DAILY_LIMIT);
    }

    /**
     * Quota is tracked per provider. Before ADR 0003 this class was hard-wired to
     * Alpha Vantage, so a second provider's calls would have silently drained the
     * 25-a-day budget the curated watchlist depends on.
     *
     * @param dailyLimit for Alpha Vantage this is a vendor-published ceiling; for
     *                   an unsanctioned keyless source it is a self-imposed
     *                   politeness cap, which is a different kind of number and is
     *                   named as such in configuration.
     */
    @Transactional
    public ApiQuotaUsage getOrCreateToday(String provider, int dailyLimit) {
        LocalDate today = LocalDate.now();
        return repo.findByProviderAndUsageDate(provider, today)
                .orElseGet(() -> repo.save(ApiQuotaUsage.builder()
                        .provider(provider)
                        .usageDate(today)
                        .callsUsed(0)
                        .callsLimit(dailyLimit)
                        .build()));
    }

    @Transactional
    public int remainingToday() {
        return remainingToday(PROVIDER, DAILY_LIMIT);
    }

    @Transactional
    public int remainingToday(String provider, int dailyLimit) {
        ApiQuotaUsage u = getOrCreateToday(provider, dailyLimit);
        return Math.max(0, u.getCallsLimit() - u.getCallsUsed());
    }

    @Transactional
    public ApiQuotaUsage resetToday() {
        ApiQuotaUsage u = getOrCreateToday();
        u.setCallsUsed(0);
        u.setCallsLimit(DAILY_LIMIT);
        return repo.save(u);
    }

    @Transactional
    public void consumeOneCall() {
        consumeOneCall(PROVIDER, DAILY_LIMIT);
    }

    @Transactional
    public void consumeOneCall(String provider, int dailyLimit) {
        ApiQuotaUsage u = getOrCreateToday(provider, dailyLimit);
        if (u.getCallsUsed() >= u.getCallsLimit()) {
            throw new IllegalStateException("Daily " + provider + " quota exhausted");
        }
        u.setCallsUsed(u.getCallsUsed() + 1);
        repo.save(u);
    }

    /**
     * Spends quota when available and reports whether it did, instead of throwing.
     * The quote path serves cached last-known prices when the budget is gone, so
     * exhaustion there is a labelled degradation rather than an error.
     *
     * <p>Concurrency-safe, unlike the read-modify-write above it. The quote path
     * fans out across many threads at once, and the find-or-create used elsewhere
     * breaks two ways under that load: every thread misses the row and races to
     * INSERT it (unique violation on provider+usage_date), and two threads that
     * both read {@code calls_used = 5} both write 6, losing a call from the count.
     * The sequential ingestion loop never exercised either path.
     *
     * <p>A single conditional UPDATE does the check and the increment inside one
     * row lock, so the budget cannot be overspent no matter how many callers
     * arrive together — and it stays correct across instances, which a
     * {@code synchronized} block would not.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryConsumeOneCall(String provider, int dailyLimit) {
        if (increment(provider) == 1) {
            return true;
        }
        // No row for today yet. Create it, tolerating the race where a sibling
        // thread got there first, then retry the increment.
        ensureTodayRow(provider, dailyLimit);
        return increment(provider) == 1;
    }

    private int increment(String provider) {
        return jdbcTemplate.update("""
                UPDATE api_quota_usage
                   SET calls_used = calls_used + 1,
                       updated_at = ?
                 WHERE provider = ?
                   AND usage_date = ?
                   AND calls_used < calls_limit
                """, Timestamp.from(Instant.now()), provider, LocalDate.now());
    }

    /**
     * Creates today's row if it is missing, tolerating a sibling thread creating it
     * first.
     *
     * <p>{@code ON CONFLICT DO NOTHING} rather than catching the duplicate-key
     * exception: in PostgreSQL a failed statement aborts the whole transaction
     * (SQLSTATE 25P02), so every later statement is rejected with "current
     * transaction is aborted" regardless of the exception being handled in Java.
     * Catching it looks correct and is not. The conflict has to be resolved inside
     * the statement, or wrapped in a savepoint.
     */
    private void ensureTodayRow(String provider, int dailyLimit) {
        String sql = h2Database ? """
                MERGE INTO api_quota_usage AS target
                USING (VALUES (?, ?, 0, ?, ?)) AS source
                    (provider, usage_date, calls_used, calls_limit, updated_at)
                   ON target.provider = source.provider AND target.usage_date = source.usage_date
                WHEN NOT MATCHED THEN INSERT
                    (provider, usage_date, calls_used, calls_limit, updated_at)
                    VALUES (source.provider, source.usage_date, source.calls_used,
                            source.calls_limit, source.updated_at)
                """ : """
                INSERT INTO api_quota_usage (provider, usage_date, calls_used, calls_limit, updated_at)
                VALUES (?, ?, 0, ?, ?)
                ON CONFLICT (provider, usage_date) DO NOTHING
                """;
        jdbcTemplate.update(sql, provider, LocalDate.now(), dailyLimit, Timestamp.from(Instant.now()));
    }
}
