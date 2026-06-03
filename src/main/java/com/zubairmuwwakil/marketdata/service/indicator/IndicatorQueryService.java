package com.zubairmuwwakil.marketdata.service.indicator;

import com.zubairmuwwakil.marketdata.model.dto.IndicatorDto;
import com.zubairmuwwakil.marketdata.model.entity.IndicatorType;
import com.zubairmuwwakil.marketdata.repository.TechnicalIndicatorRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;

@Service
public class IndicatorQueryService {

    private final TechnicalIndicatorRepository repo;

    public IndicatorQueryService(TechnicalIndicatorRepository repo) {
        this.repo = repo;
    }

    public List<IndicatorDto> getAllForSymbol(String symbol) {
        return repo.findAllBySymbolOrderByTradeDateAsc(symbol).stream()
                .map(i -> new IndicatorDto(
                        i.getSymbol(),
                        i.getTradeDate(),
                        i.getIndicatorType().name(),
                        i.getValue()
                ))
                .toList();
    }

    public List<IndicatorDto> getAllForSymbol(String symbol, LocalDate after, int limit) {
        if (after == null) {
            return repo.findAllBySymbolOrderByTradeDateAsc(symbol).stream()
                    .limit(limit)
                    .map(i -> new IndicatorDto(
                            i.getSymbol(),
                            i.getTradeDate(),
                            i.getIndicatorType().name(),
                            i.getValue()
                    ))
                    .toList();
        }

        var pageable = PageRequest.of(0, limit, Sort.by("tradeDate").ascending());
        return repo.findAllBySymbolAndTradeDateGreaterThanOrderByTradeDateAsc(symbol, after, pageable)
                .stream()
                .map(i -> new IndicatorDto(
                        i.getSymbol(),
                        i.getTradeDate(),
                        i.getIndicatorType().name(),
                        i.getValue()
                ))
                .toList();
    }

    public List<IndicatorDto> getByType(String symbol, IndicatorType type) {
        return repo.findAllBySymbolAndIndicatorTypeOrderByTradeDateAsc(symbol, type).stream()
                .map(i -> new IndicatorDto(
                        i.getSymbol(),
                        i.getTradeDate(),
                        i.getIndicatorType().name(),
                        i.getValue()
                ))
                .toList();
    }

    public List<IndicatorDto> getByType(String symbol, IndicatorType type, LocalDate after, int limit) {
        if (after == null) {
            return repo.findAllBySymbolAndIndicatorTypeOrderByTradeDateAsc(symbol, type).stream()
                    .limit(limit)
                    .map(i -> new IndicatorDto(
                            i.getSymbol(),
                            i.getTradeDate(),
                            i.getIndicatorType().name(),
                            i.getValue()
                    ))
                    .toList();
        }

        var pageable = PageRequest.of(0, limit, Sort.by("tradeDate").ascending());
        return repo.findAllBySymbolAndIndicatorTypeAndTradeDateGreaterThanOrderByTradeDateAsc(symbol, type, after, pageable)
                .stream()
                .map(i -> new IndicatorDto(
                        i.getSymbol(),
                        i.getTradeDate(),
                        i.getIndicatorType().name(),
                        i.getValue()
                ))
                .toList();
    }
}
