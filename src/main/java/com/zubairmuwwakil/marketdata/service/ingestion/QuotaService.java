package com.zubairmuwwakil.marketdata.service.ingestion;

import com.zubairmuwwakil.marketdata.model.entity.ApiQuotaUsage;
import com.zubairmuwwakil.marketdata.repository.ApiQuotaUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class QuotaService {

    public static final String PROVIDER = "ALPHAVANTAGE";
    public static final int DAILY_LIMIT = 25;

    private final ApiQuotaUsageRepository repo;

    public QuotaService(ApiQuotaUsageRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public ApiQuotaUsage getOrCreateToday() {
        LocalDate today = LocalDate.now();
        return repo.findByProviderAndUsageDate(PROVIDER, today)
                .orElseGet(() -> repo.save(ApiQuotaUsage.builder()
                        .provider(PROVIDER)
                        .usageDate(today)
                        .callsUsed(0)
                        .callsLimit(DAILY_LIMIT)
                        .build()));
    }

    @Transactional
    public int remainingToday() {
        ApiQuotaUsage u = getOrCreateToday();
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
        ApiQuotaUsage u = getOrCreateToday();
        if (u.getCallsUsed() >= u.getCallsLimit()) {
            throw new IllegalStateException("Daily Alpha Vantage quota exhausted");
        }
        u.setCallsUsed(u.getCallsUsed() + 1);
        repo.save(u);
    }
}
