package com.zubairmuwwakil.marketdata.repository;

import com.zubairmuwwakil.marketdata.model.entity.ApiQuotaUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ApiQuotaUsageRepository extends JpaRepository<ApiQuotaUsage, Long> {
    Optional<ApiQuotaUsage> findByProviderAndUsageDate(String provider, LocalDate usageDate);
}