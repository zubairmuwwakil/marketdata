package com.zubairmuwwakil.marketdata.repository;

import com.zubairmuwwakil.marketdata.model.entity.CorporateAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CorporateActionRepository extends JpaRepository<CorporateAction, Long> {

    List<CorporateAction> findAllBySymbolOrderByActionDateAsc(String symbol);

    List<CorporateAction> findAllBySymbolAndActionDateBetweenOrderByActionDateAsc(
            String symbol,
            LocalDate from,
            LocalDate to
    );
}