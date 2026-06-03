package com.zubairmuwwakil.marketdata.repository;

import com.zubairmuwwakil.marketdata.model.entity.WatchlistSymbol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistSymbolRepository extends JpaRepository<WatchlistSymbol, Long> {
    List<WatchlistSymbol> findAllByActiveTrueOrderBySymbolAsc();
    Optional<WatchlistSymbol> findBySymbol(String symbol);
    void deleteBySymbol(String symbol);
}